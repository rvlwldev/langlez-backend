package com.langlez.notification.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.Notificator
import com.langlez.member.domain.MemberRepository
import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NotificatorImpl(
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    private val fcmPushSender: FcmPushSender,
    private val objectMapper: ObjectMapper
) : Notificator {
    private val logger = LoggerFactory.getLogger(NotificatorImpl::class.java)

    override fun notify(memberId: Long, type: String, title: String, body: String, data: Map<String, String>) {
        val member = memberRepository.findById(memberId) ?: run {
            logger.warn("Recipient member not found for notification. memberId: {}", memberId)
            return
        }

        val fcmToken = member.fcmToken
        if (!fcmToken.isNullOrBlank()) {
            try {
                fcmPushSender.send(fcmToken, title, body, data)
            } catch (e: Exception) {
                logger.warn("Failed to send FCM push notification in NotificatorImpl: {}", e.message)
            }
        }

        val jsonData = if (data.isNotEmpty()) {
            try {
                objectMapper.writeValueAsString(data)
            } catch (e: Exception) {
                logger.warn("Failed to serialize notification data: {}", e.message)
                null
            }
        } else {
            null
        }

        notificationRepository.save(
            Notification(
                recipientId = memberId,
                type = type,
                title = title,
                body = body,
                data = jsonData
            )
        )
    }
}
