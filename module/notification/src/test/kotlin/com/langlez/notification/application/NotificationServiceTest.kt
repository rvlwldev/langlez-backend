package com.langlez.notification.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.MessageBroadcaster
import com.langlez.core.OnlineTracker
import com.langlez.core.PushTokenQuery
import com.langlez.core.event.chat.ChatMessageSentEvent
import com.langlez.core.event.relationship.MemberFollowedEvent
import com.langlez.exception.LanglezException
import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationRepository
import com.langlez.notification.domain.PushSender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
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

                verify(exactly = 0) { repo.save(any()) }
                verify(exactly = 0) { broadcaster.broadcast(any(), any()) }
                verify(exactly = 0) { push.send(any(), any(), any(), any()) }
            }
        }

        When("수신자가 다른 화면을 보고 있으면") {
            Then("인앱 브로드캐스트와 FCM 푸시가 항상 같이 나간다 (포그라운드에서는 OS 가 FCM 배너를 억제해 중복 노출이 없다)") {
                every { tracker.viewers(any()) } returns emptySet()
                every { tokens.findPushToken(2L) } returns "fcm-token"

                val data = slot<Map<String, String>>()
                every { push.send("fcm-token", any(), "안녕", capture(data)) } returns Unit

                service.onChatMessage(event())

                verify { broadcaster.broadcast("/topic/notification/2", any()) }
                verify { push.send("fcm-token", any(), "안녕", any()) }
                data.captured["roomId"] shouldBe "7"
                data.captured["messageId"] shouldBe "m1"
                verify { repo.save(any()) }
            }
        }

        When("FCM 토큰이 없으면") {
            Then("푸시는 건너뛰지만 인앱 브로드캐스트와 알림 이력은 남는다") {
                every { tracker.viewers(any()) } returns emptySet()
                every { tokens.findPushToken(2L) } returns null

                val saved = slot<Notification>()
                every { repo.save(capture(saved)) } answers { firstArg() }

                service.onChatMessage(event())

                verify(exactly = 0) { push.send(any(), any(), any(), any()) }
                verify { broadcaster.broadcast("/topic/notification/2", any()) }
                saved.captured.recipientId shouldBe 2L
                saved.captured.type shouldBe "CHAT_MESSAGE"
                saved.captured.body shouldBe "안녕"
                saved.captured.data!! shouldContain "\"roomId\":\"7\""
            }
        }

        When("FCM 전송이 실패하면") {
            Then("예외를 밖으로 던지지 않는다 (이력은 이미 남았고 재시도해도 죽은 토큰은 살아나지 않는다)") {
                every { tracker.viewers(any()) } returns emptySet()
                every { tokens.findPushToken(2L) } returns "dead-token"
                every { push.send(any(), any(), any(), any()) } throws IllegalStateException("boom")

                service.onChatMessage(event())

                verify { repo.save(any()) }
                verify { broadcaster.broadcast("/topic/notification/2", any()) }
            }
        }
    }

    Given("팔로우 알림이 도착하면") {

        val followed = MemberFollowedEvent(followId = 30L, followerId = 1L, followedId = 2L)

        When("팔로우당한 사람에게 알림을 보내면") {
            Then("인앱 브로드캐스트와 FCM 푸시가 항상 같이 나간다") {
                every { tokens.findPushToken(2L) } returns "fcm-token"

                val data = slot<Map<String, String>>()
                every { push.send("fcm-token", any(), any(), capture(data)) } returns Unit

                service.onMemberFollowed(followed)

                verify { broadcaster.broadcast("/topic/notification/2", any()) }
                data.captured["followerId"] shouldBe "1"
            }
        }

        When("알림 이력을 남길 때") {
            Then("제목은 메시지 키고 본문은 비어 있다 (표시 문구는 클라이언트가 조립한다)") {
                every { tokens.findPushToken(2L) } returns null

                val saved = slot<Notification>()
                every { repo.save(capture(saved)) } answers { firstArg() }

                service.onMemberFollowed(followed)

                saved.captured.recipientId shouldBe 2L
                saved.captured.type shouldBe "MEMBER_FOLLOWED"
                saved.captured.title shouldBe "notification.member-followed"
                saved.captured.body shouldBe ""
                saved.captured.data!! shouldContain "\"followerId\":\"1\""
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
