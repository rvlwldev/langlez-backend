package com.langlez.notification.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.langlez.core.MessageDeduplicator
import com.langlez.core.event.chat.ChatMessageSentEvent
import com.langlez.core.event.relationship.MemberFollowedEvent
import com.langlez.notification.application.NotificationService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.Runs
import io.mockk.andThenJust
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class NotificationConsumerTest : BehaviorSpec({

    val service = mockk<NotificationService>(relaxed = true)

    // SETNX 를 흉내 내는 로컬 대역. relaxed 목이면 isDuplicate 가 늘 false 라
    // "두 번 들어와도 한 번만" 이 검증되지 않는다.
    val seen = mutableSetOf<String>()
    val dedup = object : MessageDeduplicator {
        override fun isDuplicate(topic: String, payload: String) = !seen.add(topic + payload)
        override fun release(topic: String, payload: String) {
            seen.remove(topic + payload)
        }
    }

    val consumer = NotificationConsumer(service, dedup, jacksonObjectMapper())

    afterEach {
        clearMocks(service, answers = false)
        seen.clear()
    }

    fun followPayload(followId: Long) = """{"followId":$followId,"followerId":1,"followedId":2}"""

    Given("chat-message-sent 토픽 메시지가 들어오면") {

        When("페이로드가 ChatMessageSentEvent JSON 이면") {
            Then("이벤트로 바꿔 서비스에 넘긴다") {
                val payload =
                    """{"roomId":7,"messageId":"m1","senderId":1,"recipientId":2,"preview":"안녕"}"""

                consumer.onChatMessageSent(payload)

                verify { service.onChatMessage(ChatMessageSentEvent(7L, "m1", 1L, 2L, "안녕")) }
            }
        }

        When("같은 메시지가 두 번 배달되면") {
            Then("알림은 한 번만 만든다") {
                val payload =
                    """{"roomId":7,"messageId":"m2","senderId":1,"recipientId":2,"preview":"안녕"}"""

                consumer.onChatMessageSent(payload)
                consumer.onChatMessageSent(payload)

                verify(exactly = 1) { service.onChatMessage(any()) }
            }
        }
    }

    Given("member-followed 토픽 메시지가 들어오면") {

        When("페이로드가 MemberFollowedEvent JSON 이면") {
            Then("이벤트로 바꿔 서비스에 넘긴다") {
                consumer.onMemberFollowed(followPayload(11L))

                verify(exactly = 1) { service.onMemberFollowed(MemberFollowedEvent(11L, 1L, 2L)) }
            }
        }

        When("같은 메시지가 두 번 배달되면") {
            Then("알림은 한 번만 만든다") {
                consumer.onMemberFollowed(followPayload(12L))
                consumer.onMemberFollowed(followPayload(12L))

                verify(exactly = 1) { service.onMemberFollowed(any()) }
            }
        }

        When("언팔로우 후 같은 상대를 다시 팔로우하면") {
            Then("followId 가 달라 알림이 또 간다") {
                consumer.onMemberFollowed(followPayload(13L))
                consumer.onMemberFollowed(followPayload(14L))

                verify(exactly = 1) { service.onMemberFollowed(MemberFollowedEvent(13L, 1L, 2L)) }
                verify(exactly = 1) { service.onMemberFollowed(MemberFollowedEvent(14L, 1L, 2L)) }
            }
        }

        When("처리 도중 예외가 나면") {
            Then("예외를 그대로 올리고, 재배달된 메시지는 중복으로 걸리지 않는다") {
                every { service.onMemberFollowed(any()) } throws
                    IllegalStateException("DB 장애") andThenJust Runs

                shouldThrow<IllegalStateException> { consumer.onMemberFollowed(followPayload(15L)) }

                consumer.onMemberFollowed(followPayload(15L))

                verify(exactly = 2) { service.onMemberFollowed(MemberFollowedEvent(15L, 1L, 2L)) }
            }
        }
    }
})
