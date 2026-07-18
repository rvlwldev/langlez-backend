package com.langlez.notification.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.*
import java.lang.RuntimeException

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
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
        providerDisplayName = "Nick $id",
        role = Member.Role.MEMBER,
        fcmToken = fcmToken
    )

    Given("notify 호출 시") {
        val memberId = 1L
        val type = "test.type"
        val title = "Test Title"
        val body = "Test Body"
        val data = mapOf("key" to "value")

        When("수신자의 fcmToken이 존재할 때") {
            val member = createMember(memberId, "fcm-token-123")
            every { memberRepository.findById(memberId) } returns member
            every { fcmPushSender.send(any(), any(), any(), any()) } just Runs
            every { notificationRepository.save(any()) } answers { firstArg() }

            Then("FcmPushSender.send와 NotificationRepository.save 모두 호출된다") {
                notificator.notify(memberId, type, title, body, data)

                verify(exactly = 1) { fcmPushSender.send("fcm-token-123", title, body, data) }
                verify(exactly = 1) { notificationRepository.save(any()) }
            }
        }

        When("수신자의 fcmToken이 존재하지 않을 때") {
            val member = createMember(memberId, null)
            every { memberRepository.findById(memberId) } returns member
            every { notificationRepository.save(any()) } answers { firstArg() }

            Then("FcmPushSender.send는 호출되지 않고 NotificationRepository.save만 호출된다") {
                notificator.notify(memberId, type, title, body, data)

                verify(exactly = 0) { fcmPushSender.send(any(), any(), any(), any()) }
                verify(exactly = 1) { notificationRepository.save(any()) }
            }
        }

        When("FcmPushSender.send가 예외를 던질 때") {
            val member = createMember(memberId, "fcm-token-123")
            every { memberRepository.findById(memberId) } returns member
            every { fcmPushSender.send(any(), any(), any(), any()) } throws RuntimeException("FCM error")
            every { notificationRepository.save(any()) } answers { firstArg() }

            Then("예외가 전파되지 않고 NotificationRepository.save는 여전히 호출된다") {
                notificator.notify(memberId, type, title, body, data)

                verify(exactly = 1) { fcmPushSender.send("fcm-token-123", title, body, data) }
                verify(exactly = 1) { notificationRepository.save(any()) }
            }
        }
    }
})
