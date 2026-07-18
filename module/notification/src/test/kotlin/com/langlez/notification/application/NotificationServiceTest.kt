package com.langlez.notification.application

import com.langlez.notification.api.NotificationResponse
import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.Instant

class NotificationServiceTest : BehaviorSpec({

    val notificationRepository = mockk<NotificationRepository>()
    val service = NotificationService(notificationRepository)

    afterEach {
        clearMocks(notificationRepository)
    }

    Given("getNotifications 호출 시") {
        val memberId = 1L
        val mockNotifications = (1..5).map { id ->
            Notification(
                id = id.toLong(),
                recipientId = memberId,
                type = "test.type",
                title = "Title $id",
                body = "Body $id",
                read = false,
                createdAt = Instant.now()
            )
        }

        When("size가 100을 초과하면") {
            every { notificationRepository.findByRecipient(memberId, null, 100) } returns mockNotifications

            Then("100으로 상한(MAX_PAGE_SIZE)이 설정되어 조회된다") {
                val result = service.getNotifications(memberId, null, 150)
                result.notifications.size shouldBe 5
                verify(exactly = 1) { notificationRepository.findByRecipient(memberId, null, 100) }
            }
        }

        When("정상적인 size로 조회하면") {
            every { notificationRepository.findByRecipient(memberId, null, 20) } returns mockNotifications

            Then("요청한 size로 조회된다") {
                val result = service.getNotifications(memberId, null, 20)
                result.notifications.size shouldBe 5
                verify(exactly = 1) { notificationRepository.findByRecipient(memberId, null, 20) }
            }
        }
    }

    Given("markAsRead 호출 시") {
        val memberId = 1L
        val notificationId = 10L

        When("메서드가 실행되면") {
            every { notificationRepository.markAsRead(memberId, notificationId) } just Runs

            Then("repository의 markAsRead를 호출한다") {
                service.markAsRead(memberId, notificationId)
                verify(exactly = 1) { notificationRepository.markAsRead(memberId, notificationId) }
            }
        }
    }

    Given("markAllAsRead 호출 시") {
        val memberId = 1L

        When("메서드가 실행되면") {
            every { notificationRepository.markAllAsRead(memberId) } just Runs

            Then("repository의 markAllAsRead를 호출한다") {
                service.markAllAsRead(memberId)
                verify(exactly = 1) { notificationRepository.markAllAsRead(memberId) }
            }
        }
    }
})
