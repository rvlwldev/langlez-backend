package com.langlez.notification.domain

interface NotificationRepository {
    fun save(notification: Notification): Notification
    fun saveAll(notifications: List<Notification>): List<Notification>
    fun findByRecipient(recipientId: Long, cursor: Long?, size: Int): List<Notification>
    fun markAsRead(recipientId: Long, notificationId: Long): Boolean
    fun markAllAsRead(recipientId: Long): Int
}
