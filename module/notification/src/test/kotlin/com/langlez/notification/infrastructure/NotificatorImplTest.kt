package com.langlez.notification.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.member.domain.Member
import com.langlez.member.application.MemberRepository
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRole
import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.*

class NotificatorImplTest : BehaviorSpec({

    val memberRepository = mockk<MemberRepository>()
    val notificationRepository = mockk<NotificationRepository>()
    val fcmPushSender = mockk<FcmPushSender>()
    val objectMapper = ObjectMapper()

    val notificator = NotificatorImpl(
        memberRepository,
        notificationRepository,
        fcmPushSender,
        objectMapper
    )

    afterEach {
        clearMocks(memberRepository, notificationRepository, fcmPushSender)
    }

    fun createMember(id: Long, fcmToken: String? = null) = Member(
        id = id,
        email = "user$id@example.com",
        username = "user$id",
        nickname = "Nick $id",
        provider = MemberProvider.GOOGLE,
        providerId = "p$id",
        providerDisplayName = "Nick $id",
        role = MemberRole.MEMBER,
        fcm = fcmToken
    )

    Given("notify 호출 시") {
        val memberId = 1L
        val type = "test.type"
        val title = "Test Title"
        val body = "Test Body"
        val data = mapOf("key" to "value")

        When("수신자의 fcmToken이 존재할 때") {
            val member = createMember(memberId, "fcm-token-123")
            every { memberRepository.findByIds(listOf(memberId)) } returns listOf(member)
            every { fcmPushSender.send(any(), any(), any(), any()) } just Runs
            every { notificationRepository.saveAll(any<List<Notification>>()) } answers { firstArg() }

            Then("FcmPushSender.send와 NotificationRepository.saveAll 모두 호출된다") {
                notificator.notify(memberId, type, title, body, data)

                verify(exactly = 1) { fcmPushSender.send("fcm-token-123", title, body, data) }
                verify(exactly = 1) { notificationRepository.saveAll(any<List<Notification>>()) }
            }
        }

        When("수신자의 fcmToken이 존재하지 않을 때") {
            val member = createMember(memberId, null)
            every { memberRepository.findByIds(listOf(memberId)) } returns listOf(member)
            every { notificationRepository.saveAll(any<List<Notification>>()) } answers { firstArg() }

            Then("FcmPushSender.send는 호출되지 않고 NotificationRepository.saveAll만 호출된다") {
                notificator.notify(memberId, type, title, body, data)

                verify(exactly = 0) { fcmPushSender.send(any(), any(), any(), any()) }
                verify(exactly = 1) { notificationRepository.saveAll(any<List<Notification>>()) }
            }
        }

        When("FcmPushSender.send 호출 시") {
            val member = createMember(memberId, "fcm-token-123")
            every { memberRepository.findByIds(listOf(memberId)) } returns listOf(member)
            every { fcmPushSender.send(any(), any(), any(), any()) } just Runs
            every { notificationRepository.saveAll(any<List<Notification>>()) } answers { firstArg() }

            Then("fcmPushSender.send에 try-catch가 없어도 비동기로 호출된다") {
                notificator.notify(memberId, type, title, body, data)

                verify(exactly = 1) { fcmPushSender.send("fcm-token-123", title, body, data) }
                verify(exactly = 1) { notificationRepository.saveAll(any<List<Notification>>()) }
            }
        }
    }

    Given("notifyAll 호출 시") {
        val memberIds = listOf(1L, 2L)
        val type = "test.type"
        val title = "Test Title"
        val body = "Test Body"
        val data = mapOf("key" to "value")

        When("다중 수신자 배치를 처리할 때") {
            val m1 = createMember(1L, "token-1")
            val m2 = createMember(2L, "token-2")
            every { memberRepository.findByIds(memberIds) } returns listOf(m1, m2)
            every { fcmPushSender.send(any(), any(), any(), any()) } just Runs
            every { notificationRepository.saveAll(any<List<Notification>>()) } answers { firstArg() }

            Then("1회 DB 조회 및 1회 배치 saveAll로 저장된다") {
                notificator.notifyAll(memberIds, type, title, body, data)

                verify(exactly = 1) { memberRepository.findByIds(memberIds) }
                verify(exactly = 1) { fcmPushSender.send("token-1", title, body, data) }
                verify(exactly = 1) { fcmPushSender.send("token-2", title, body, data) }
                verify(exactly = 1) { notificationRepository.saveAll(any<List<Notification>>()) }
            }
        }
    }
})
