package com.langlez.notification.domain

interface NotificationRepository {
    fun save(notification: Notification): Notification
    fun findByRecipient(recipientId: Long, cursor: Long?, size: Int): List<Notification>
    fun markAsRead(recipientId: Long, notificationId: Long)
    fun markAllAsRead(recipientId: Long)
}
