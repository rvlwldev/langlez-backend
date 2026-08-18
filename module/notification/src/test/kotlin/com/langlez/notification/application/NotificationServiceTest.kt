package com.langlez.notification.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.MessageBroadcaster
import com.langlez.core.OnlineTracker
import com.langlez.core.PushTokenQuery
import com.langlez.core.event.chat.ChatMessageSentEvent
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

        When("앱은 켜져 있지만 다른 화면을 보고 있으면") {
            Then("인앱 알림만 보내고 푸시는 보내지 않는다") {
                every { tracker.viewers(any()) } returns emptySet()
                every { tracker.checkOnline(2L) } returns mapOf(2L to true)

                service.onChatMessage(event())

                verify { broadcaster.broadcast("/topic/notification/2", any()) }
                verify(exactly = 0) { push.send(any(), any(), any(), any()) }
                verify { repo.save(any()) }
            }
        }

        When("앱을 켜지 않았으면") {
            Then("FCM 푸시를 보내고 인앱 알림은 보내지 않는다") {
                every { tracker.viewers(any()) } returns emptySet()
                every { tracker.checkOnline(2L) } returns mapOf(2L to false)
                every { tokens.findPushToken(2L) } returns "fcm-token"

                val data = slot<Map<String, String>>()
                every { push.send("fcm-token", any(), "안녕", capture(data)) } returns Unit

                service.onChatMessage(event())

                data.captured["roomId"] shouldBe "7"
                data.captured["messageId"] shouldBe "m1"
                verify(exactly = 0) { broadcaster.broadcast(any(), any()) }
            }
        }

        When("앱을 켜지 않았는데 FCM 토큰이 없으면") {
            Then("푸시는 건너뛰지만 알림 이력은 남는다") {
                every { tracker.viewers(any()) } returns emptySet()
                every { tracker.checkOnline(2L) } returns mapOf(2L to false)
                every { tokens.findPushToken(2L) } returns null

                val saved = slot<Notification>()
                every { repo.save(capture(saved)) } answers { firstArg() }

                service.onChatMessage(event())

                verify(exactly = 0) { push.send(any(), any(), any(), any()) }
                saved.captured.recipientId shouldBe 2L
                saved.captured.type shouldBe "CHAT_MESSAGE"
                saved.captured.body shouldBe "안녕"
                saved.captured.data!! shouldContain "\"roomId\":\"7\""
            }
        }

        When("FCM 전송이 실패하면") {
            Then("예외를 밖으로 던지지 않는다 (이력은 이미 남았고 재시도해도 죽은 토큰은 살아나지 않는다)") {
                every { tracker.viewers(any()) } returns emptySet()
                every { tracker.checkOnline(2L) } returns mapOf(2L to false)
                every { tokens.findPushToken(2L) } returns "dead-token"
                every { push.send(any(), any(), any(), any()) } throws IllegalStateException("boom")

                service.onChatMessage(event())

                verify { repo.save(any()) }
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
