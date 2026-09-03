package com.langlez.notification.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.chat.contract.ChatMessageSentEvent
import com.langlez.core.MessageBroadcaster
import com.langlez.exception.LanglezException
import com.langlez.member.contract.OnlineTracker
import com.langlez.member.contract.PushTokenReader
import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationMuteRepository
import com.langlez.notification.domain.NotificationRepository
import com.langlez.notification.domain.NotificationSetting
import com.langlez.notification.domain.NotificationSettingRepository
import com.langlez.notification.domain.PushSender
import com.langlez.follow.contract.MemberFollowedEvent
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
import java.time.LocalTime

class NotificationServiceTest : BehaviorSpec({

    val repo = mockk<NotificationRepository>()
    val tracker = mockk<OnlineTracker>()
    val broadcaster = mockk<MessageBroadcaster>(relaxed = true)
    val tokens = mockk<PushTokenReader>()
    val push = mockk<PushSender>(relaxed = true)
    val mutes = mockk<NotificationMuteRepository>()
    val settingsRepo = mockk<NotificationSettingRepository>()

    val service = NotificationService(repo, tracker, broadcaster, tokens, push, ObjectMapper(), mutes, settingsRepo)

    // answers = false 라 여기 둔 스텁은 유지된다. 호출 기록만 매 테스트마다 지워진다.
    every { repo.save(any()) } answers { firstArg() }
    every { repo.saveAll(any()) } answers { firstArg<Collection<Notification>>().toList() }
    // 기본은 "설정 없음" — mute 도 quiet 도 없어 기존 테스트들의 발송 동작이 그대로 유지된다.
    every { mutes.findAll(any()) } returns emptyMap()
    every { settingsRepo.findAll(any()) } returns emptyList()

    afterEach { clearMocks(repo, tracker, broadcaster, tokens, push, mutes, settingsRepo, answers = false) }

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

    Given("수신자가 그 유형을 mute 했을 때") {
        // mutes/settingsRepo 를 이 Given 전용으로 새로 만든다 — 상위 스펙의 mutes/settingsRepo 를
        // 그대로 쓰면 afterEach 가 스텁까지는 안 지워서(answers = false) 다른 Given 블록이 같은
        // 회원 id 로 findAll 을 스텁해 둔 게 새어 들어올 수 있다. 인스턴스를 분리하면 그 경로 자체가 없다.
        val mutes = mockk<NotificationMuteRepository>()
        val settingsRepo = mockk<NotificationSettingRepository>()
        val service = NotificationService(repo, tracker, broadcaster, tokens, push, ObjectMapper(), mutes, settingsRepo)
        every { settingsRepo.findAll(any()) } returns emptyList()

        afterEach { clearMocks(mutes, settingsRepo, answers = false) }

        When("notifyAll 을 호출하면") {
            Then("이력도 안 남고 브로드캐스트도 푸시도 안 간다") {
                every { mutes.findAll(setOf(1L, 2L)) } returns mapOf(1L to setOf("CHAT_MESSAGE"))
                every { tokens.findPushTokens(setOf(2L)) } returns mapOf(2L to "token-2")

                service.notifyAll(listOf(1L, 2L), "CHAT_MESSAGE", "title", "body", emptyMap())

                verify(exactly = 1) { repo.saveAll(match { it.size == 1 && it.first().recipientId == 2L }) }
                verify(exactly = 1) { broadcaster.broadcast(any(), any()) }
                verify { tokens.findPushTokens(setOf(2L)) }
            }
        }

        When("전원이 그 유형을 mute 했으면") {
            Then("아무 협력자도 호출하지 않는다") {
                every { mutes.findAll(setOf(1L)) } returns mapOf(1L to setOf("CHAT_MESSAGE"))

                service.notifyAll(listOf(1L), "CHAT_MESSAGE", "title", "body", emptyMap())

                verify(exactly = 0) { repo.saveAll(any()) }
                verify(exactly = 0) { broadcaster.broadcast(any(), any()) }
                verify(exactly = 0) { tokens.findPushTokens(any()) }
            }
        }

        When("mute 설정 조회가 실패하면") {
            Then("전부 발송한다 (fail-open)") {
                every { mutes.findAll(setOf(1L)) } throws IllegalStateException("boom")
                every { tokens.findPushTokens(setOf(1L)) } returns mapOf(1L to "token-1")

                service.notifyAll(listOf(1L), "CHAT_MESSAGE", "title", "body", emptyMap())

                verify(exactly = 1) { repo.saveAll(match { it.size == 1 }) }
                verify { broadcaster.broadcast("/topic/notification/1", any()) }
            }
        }
    }

    Given("수신자가 방해금지 시간대일 때") {
        // 위 mute Given 블록과 같은 이유로 mutes/settingsRepo 를 이 Given 전용으로 새로 만든다.
        val mutes = mockk<NotificationMuteRepository>()
        val settingsRepo = mockk<NotificationSettingRepository>()
        val service = NotificationService(repo, tracker, broadcaster, tokens, push, ObjectMapper(), mutes, settingsRepo)
        every { mutes.findAll(any()) } returns emptyMap()

        afterEach { clearMocks(mutes, settingsRepo, answers = false) }

        fun quiet(memberId: Long, quiet: Boolean): NotificationSetting {
            val setting = mockk<NotificationSetting>()
            every { setting.memberId } returns memberId
            every { setting.isQuietAt(any()) } returns quiet
            return setting
        }

        When("notifyAll 을 호출하면") {
            Then("이력·브로드캐스트는 남지만 그 사람에게만 푸시가 안 간다") {
                every { settingsRepo.findAll(setOf(1L, 2L)) } returns listOf(quiet(1L, true), quiet(2L, false))
                every { tokens.findPushTokens(setOf(2L)) } returns mapOf(2L to "token-2")

                val saved = slot<Collection<Notification>>()
                every { repo.saveAll(capture(saved)) } answers { firstArg<Collection<Notification>>().toList() }

                service.notifyAll(listOf(1L, 2L), "CHAT_MESSAGE", "title", "body", emptyMap())

                saved.captured.size shouldBe 2
                verify(exactly = 2) { broadcaster.broadcast(any(), any()) }
                verify { tokens.findPushTokens(setOf(2L)) }
            }
        }

        When("방해금지 설정 조회가 실패하면") {
            Then("푸시는 전부 보낸다 (fail-open)") {
                every { settingsRepo.findAll(setOf(1L)) } throws IllegalStateException("boom")
                every { tokens.findPushTokens(setOf(1L)) } returns mapOf(1L to "token-1")

                service.notifyAll(listOf(1L), "CHAT_MESSAGE", "title", "body", emptyMap())

                verify { tokens.findPushTokens(setOf(1L)) }
            }
        }
    }

    Given("알림 수신 설정 조회 시") {
        When("설정한 적 없으면") {
            Then("전부 켠 상태(빈 mute)와 방해금지 없음으로 나온다") {
                every { mutes.find(5L) } returns emptySet()
                every { settingsRepo.find(5L) } returns null

                val snapshot = service.settingsOf(5L)

                snapshot.mutedTypes shouldBe emptySet()
                snapshot.quietFrom shouldBe null
                snapshot.timeZone shouldBe null
            }
        }
    }

    Given("끌 알림 유형을 바꿀 때") {
        When("알 수 없는 유형이면") {
            Then("400 이 나고 저장하지 않는다") {
                val ex = shouldThrow<LanglezException> { service.updateMutes(5L, setOf("UNKNOWN_TYPE")) }

                ex.status.value() shouldBe 400
                verify(exactly = 0) { mutes.replaceAll(any(), any()) }
            }
        }

        When("유효한 유형 목록이면") {
            Then("전체 교체되고 그대로 돌려준다") {
                every { mutes.replaceAll(5L, setOf("CHAT_MESSAGE")) } returns Unit

                service.updateMutes(5L, setOf("CHAT_MESSAGE")) shouldBe setOf("CHAT_MESSAGE")

                verify { mutes.replaceAll(5L, setOf("CHAT_MESSAGE")) }
            }
        }
    }

    Given("방해금지 시간대를 바꿀 때") {
        When("알 수 없는 타임존을 주면") {
            Then("400 이 난다") {
                shouldThrow<LanglezException> {
                    service.updateQuietHours(5L, LocalTime.of(22, 0), LocalTime.of(7, 0), "Not/AZone")
                }.status.value() shouldBe 400
            }
        }

        When("from 만 주고 to 를 안 주면") {
            Then("400 이 난다") {
                every { settingsRepo.find(5L) } returns null

                shouldThrow<LanglezException> {
                    service.updateQuietHours(5L, LocalTime.of(22, 0), null, "Asia/Seoul")
                }.status.value() shouldBe 400
            }
        }

        When("from 과 to 가 같으면") {
            Then("400 이 난다 (24시간 방해금지는 유형 mute 로 하는 것이 맞다)") {
                every { settingsRepo.find(5L) } returns null

                shouldThrow<LanglezException> {
                    service.updateQuietHours(5L, LocalTime.of(0, 0), LocalTime.of(0, 0), "Asia/Seoul")
                }.status.value() shouldBe 400
            }
        }

        When("유효한 요청이면") {
            Then("저장되고 그대로 돌려준다") {
                every { settingsRepo.find(5L) } returns null
                every { settingsRepo.save(any()) } answers { firstArg() }

                val setting = service.updateQuietHours(5L, LocalTime.of(22, 0), LocalTime.of(7, 0), "Asia/Seoul")

                setting.quietFrom shouldBe LocalTime.of(22, 0)
                setting.quietTo shouldBe LocalTime.of(7, 0)
                setting.timeZone shouldBe "Asia/Seoul"
            }
        }

        When("둘 다 null 이면") {
            Then("기존 설정을 해제한다") {
                every { settingsRepo.find(5L) } returns
                    NotificationSetting(memberId = 5L, quietFrom = LocalTime.of(22, 0), quietTo = LocalTime.of(7, 0), timeZone = "Asia/Seoul")
                every { settingsRepo.save(any()) } answers { firstArg() }

                val setting = service.updateQuietHours(5L, null, null, null)

                setting.quietFrom shouldBe null
                setting.quietTo shouldBe null
                setting.timeZone shouldBe null
            }
        }
    }
})
