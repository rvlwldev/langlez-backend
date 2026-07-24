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
        notifyAll(listOf(memberId), type, title, body, data)
    }

    fun notifyAll(memberIds: List<Long>, type: String, title: String, body: String, data: Map<String, String> = emptyMap()) {
        if (memberIds.isEmpty()) return

        val members = memberRepository.findByIds(memberIds)
        if (members.isEmpty()) return

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

        for (member in members) {
            val fcmToken = member.fcmToken
            if (!fcmToken.isNullOrBlank()) {
                fcmPushSender.send(fcmToken, title, body, data)
            }
        }

        val notifications = members.map { member ->
            Notification(
                recipientId = member.id,
                type = type,
                title = title,
                body = body,
                data = jsonData
            )
        }

        notificationRepository.saveAll(notifications)
    }
}
