package com.langlez.notification.application

import com.langlez.core.LanglezException
import com.langlez.notification.api.NotificationResponse
import com.langlez.notification.domain.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository
) {

    @Transactional(readOnly = true)
    fun getNotifications(memberId: Long, cursor: Long?, size: Int): NotificationResponse.CursorList {
        val boundedSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val notifications = notificationRepository.findByRecipient(memberId, cursor, boundedSize)
        val dtos = notifications.map {
            NotificationResponse.NotificationDto(
                notificationId = it.id,
                type = it.type,
                title = it.title,
                body = it.body,
                read = it.read,
                data = it.data,
                createdAt = it.createdAt
            )
        }
        val nextCursor = if (notifications.size == boundedSize) notifications.lastOrNull()?.id else null
        return NotificationResponse.CursorList(nextCursor, dtos)
    }

    @Transactional
    fun markAsRead(memberId: Long, notificationId: Long) {
        val updated = notificationRepository.markAsRead(memberId, notificationId)
        if (!updated) {
            throw LanglezException(404, "notification.not-found")
        }
    }

    @Transactional
    fun markAllAsRead(memberId: Long): Int {
        return notificationRepository.markAllAsRead(memberId)
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
