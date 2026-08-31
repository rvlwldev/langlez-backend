package com.langlez.notification.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.chat.contract.ChatMessageSentEvent
import com.langlez.core.MessageBroadcaster
import com.langlez.exception.LanglezException
import com.langlez.member.contract.OnlineTracker
import com.langlez.member.contract.PushTokenQuery
import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationRepository
import com.langlez.notification.domain.PushSender
import com.langlez.relationship.contract.MemberFollowedEvent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class NotificationServiceTest : BehaviorSpec({

    val repo = mockk<NotificationRepository>()
    val tracker = mockk<OnlineTracker>()
    val broadcaster = mockk<MessageBroadcaster>(relaxed = true)
    val tokens = mockk<PushTokenQuery>()
    val push = mockk<PushSender>(relaxed = true)

    val service = NotificationService(repo, tracker, broadcaster, tokens, push, ObjectMapper())

    // answers = false 라 여기 둔 스텁은 유지된다. 호출 기록만 매 테스트마다 지워진다.
    every { repo.save(any()) } answers { firstArg() }
    every { repo.saveAll(any()) } answers { firstArg<Collection<Notification>>().toList() }

    afterEach { clearMocks(repo, tracker, broadcaster, tokens, push, answers = false) }

    fun event(recipientId: Long = 2L, roomId: Long = 7L) =
        ChatMessageSentEvent(roomId = roomId, messageId = "m1", senderId = 1L, recipientId = recipientId, preview = "안녕")

    fun notification(id: Long = 1L, recipientId: Long = 2L, read: Boolean = false) = Notification(
        id = id,
        recipientId = recipientId,
        type = "CHAT_MESSAGE",
        title = "notification.chat-message.title",
        body = "안녕",
        read = read,
    )

    Given("채팅 메시지 알림이 도착하면") {

        When("수신자가 그 방을 보고 있으면") {
            Then("아무것도 보내지 않고 이력도 남기지 않는다") {
                every { tracker.viewers("/topic/chat/room/7") } returns setOf(2L)

                service.onChatMessage(event())

                verify(exactly = 0) { repo.saveAll(any()) }
                verify(exactly = 0) { broadcaster.broadcast(any(), any()) }
                verify(exactly = 0) { push.sendAll(any(), any(), any(), any()) }
            }
        }

        When("수신자가 다른 화면을 보고 있으면") {
            Then("인앱 브로드캐스트와 FCM 푸시가 항상 같이 나간다 (포그라운드에서는 OS 가 FCM 배너를 억제해 중복 노출이 없다)") {
                every { tracker.viewers(any()) } returns emptySet()
                every { tokens.findPushTokens(setOf(2L)) } returns mapOf(2L to "fcm-token")

                val tokenArg = slot<Collection<String>>()
                val data = slot<Map<String, String>>()
                every { push.sendAll(capture(tokenArg), any(), "안녕", capture(data)) } returns emptyList()

                service.onChatMessage(event())

                verify { broadcaster.broadcast("/topic/notification/2", any()) }
                tokenArg.captured shouldContainExactlyInAnyOrder listOf("fcm-token")
                data.captured["roomId"] shouldBe "7"
                data.captured["messageId"] shouldBe "m1"
                verify { repo.saveAll(any()) }
            }
        }

        When("FCM 토큰이 없으면") {
            Then("푸시는 건너뛰지만 인앱 브로드캐스트와 알림 이력은 남는다") {
                every { tracker.viewers(any()) } returns emptySet()
                every { tokens.findPushTokens(setOf(2L)) } returns emptyMap()

                val saved = slot<Collection<Notification>>()
                every { repo.saveAll(capture(saved)) } answers { firstArg<Collection<Notification>>().toList() }

                service.onChatMessage(event())

                verify(exactly = 0) { push.sendAll(any(), any(), any(), any()) }
                verify { broadcaster.broadcast("/topic/notification/2", any()) }
                saved.captured.single().recipientId shouldBe 2L
                saved.captured.single().type shouldBe "CHAT_MESSAGE"
                saved.captured.single().body shouldBe "안녕"
                saved.captured.single().data!! shouldContain "\"roomId\":\"7\""
            }
        }

        When("FCM 전송이 실패하면") {
            Then("예외를 밖으로 던지지 않는다 (이력은 이미 남았고 재시도해도 죽은 토큰은 살아나지 않는다)") {
                every { tracker.viewers(any()) } returns emptySet()
                every { tokens.findPushTokens(setOf(2L)) } returns mapOf(2L to "dead-token")
                every { push.sendAll(any(), any(), any(), any()) } throws IllegalStateException("boom")

                service.onChatMessage(event())

                verify { repo.saveAll(any()) }
                verify { broadcaster.broadcast("/topic/notification/2", any()) }
            }
        }
    }

    Given("팔로우 알림이 도착하면") {

        val followed = MemberFollowedEvent(followId = 30L, followerId = 1L, followedId = 2L)

        When("팔로우당한 사람에게 알림을 보내면") {
            Then("인앱 브로드캐스트와 FCM 푸시가 항상 같이 나간다") {
                every { tokens.findPushTokens(setOf(2L)) } returns mapOf(2L to "fcm-token")

                val data = slot<Map<String, String>>()
                every { push.sendAll(any(), any(), any(), capture(data)) } returns emptyList()

                service.onMemberFollowed(followed)

                verify { broadcaster.broadcast("/topic/notification/2", any()) }
                data.captured["followerId"] shouldBe "1"
            }
        }

        When("알림 이력을 남길 때") {
            Then("제목은 메시지 키고 본문은 비어 있다 (표시 문구는 클라이언트가 조립한다)") {
                every { tokens.findPushTokens(setOf(2L)) } returns emptyMap()

                val saved = slot<Collection<Notification>>()
                every { repo.saveAll(capture(saved)) } answers { firstArg<Collection<Notification>>().toList() }

                service.onMemberFollowed(followed)

                saved.captured.single().recipientId shouldBe 2L
                saved.captured.single().type shouldBe "MEMBER_FOLLOWED"
                saved.captured.single().title shouldBe "notification.member-followed"
                saved.captured.single().body shouldBe ""
                saved.captured.single().data!! shouldContain "\"followerId\":\"1\""
            }
        }
    }

    Given("여러 수신자에게 같은 알림을 한 번에 보낼 때") {

        When("수신자 3명에게 notifyAll 을 호출하면") {
            Then("이력 3건, 브로드캐스트 3회, sendAll 1회로 끝난다") {
                every { tokens.findPushTokens(setOf(1L, 2L, 3L)) } returns mapOf(
                    1L to "token-1", 2L to "token-2", 3L to "token-3",
                )
                every { push.sendAll(any(), any(), any(), any()) } returns emptyList()

                val saved = slot<Collection<Notification>>()
                every { repo.saveAll(capture(saved)) } answers { firstArg<Collection<Notification>>().toList() }

                service.notifyAll(listOf(1L, 2L, 3L), "SYSTEM", "title", "body", emptyMap())

                saved.captured.size shouldBe 3
                verify(exactly = 3) { broadcaster.broadcast(any(), any()) }
                verify(exactly = 1) { push.sendAll(any(), "title", "body", any()) }
            }
        }

        When("일부 수신자에게 토큰이 없으면") {
            Then("그 사람은 푸시에서 빠지지만 이력과 인앱 알림은 정상이다") {
                every { tokens.findPushTokens(setOf(1L, 2L)) } returns mapOf(1L to "token-1")

                val data = slot<Collection<String>>()
                every { push.sendAll(capture(data), any(), any(), any()) } returns emptyList()

                service.notifyAll(listOf(1L, 2L), "SYSTEM", "title", "body", emptyMap())

                data.captured shouldContainExactlyInAnyOrder listOf("token-1")
                verify(exactly = 1) { repo.saveAll(match { it.size == 2 }) }
                verify(exactly = 2) { broadcaster.broadcast(any(), any()) }
            }
        }

        When("중복된 id 가 섞여 있으면") {
            Then("한 번만 처리한다") {
                every { tokens.findPushTokens(setOf(1L)) } returns mapOf(1L to "token-1")
                every { push.sendAll(any(), any(), any(), any()) } returns emptyList()

                val saved = slot<Collection<Notification>>()
                every { repo.saveAll(capture(saved)) } answers { firstArg<Collection<Notification>>().toList() }

                service.notifyAll(listOf(1L, 1L, 1L), "SYSTEM", "title", "body", emptyMap())

                saved.captured.size shouldBe 1
                verify(exactly = 1) { broadcaster.broadcast(any(), any()) }
                verify { tokens.findPushTokens(setOf(1L)) }
            }
        }

        When("수신자 목록이 비어 있으면") {
            Then("아무 협력자도 호출하지 않는다") {
                service.notifyAll(emptyList(), "SYSTEM", "title", "body", emptyMap())

                verify(exactly = 0) { repo.saveAll(any()) }
                verify(exactly = 0) { broadcaster.broadcast(any(), any()) }
                verify(exactly = 0) { tokens.findPushTokens(any()) }
                verify(exactly = 0) { push.sendAll(any(), any(), any(), any()) }
            }
        }

        When("sendAll 이 예외를 던지면") {
            Then("이력과 브로드캐스트는 이미 끝났으므로 영향받지 않는다") {
                every { tokens.findPushTokens(setOf(1L, 2L)) } returns mapOf(1L to "token-1", 2L to "token-2")
                every { push.sendAll(any(), any(), any(), any()) } throws IllegalStateException("boom")

                val saved = slot<Collection<Notification>>()
                every { repo.saveAll(capture(saved)) } answers { firstArg<Collection<Notification>>().toList() }

                service.notifyAll(listOf(1L, 2L), "SYSTEM", "title", "body", emptyMap())

                saved.captured.size shouldBe 2
                verify(exactly = 2) { broadcaster.broadcast(any(), any()) }
            }
        }
    }

    Given("알림 읽음 처리 시") {

        When("남의 알림을 읽음 처리하려 하면") {
            Then("403 이 나고 저장하지 않는다") {
                every { repo.find(1L) } returns notification(recipientId = 99L)

                val ex = shouldThrow<LanglezException> { service.markRead(memberId = 2L, id = 1L) }

                ex.status.value() shouldBe 403
                verify(exactly = 0) { repo.save(any()) }
            }
        }

        When("없는 알림을 읽음 처리하려 하면") {
            Then("404 가 난다") {
                every { repo.find(1L) } returns null

                shouldThrow<LanglezException> { service.markRead(memberId = 2L, id = 1L) }
                    .status.value() shouldBe 404
            }
        }

        When("내 알림이면") {
            Then("읽음으로 저장된다") {
                every { repo.find(1L) } returns notification()

                val saved = slot<Notification>()
                every { repo.save(capture(saved)) } answers { firstArg() }

                service.markRead(memberId = 2L, id = 1L)

                saved.captured.read shouldBe true
            }
        }
    }
})
