package com.langlez.notification.infrastructure

import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationRepository
import com.langlez.notification.infrastructure.jpa.NotificationJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class NotificationRepositoryImpl(
    private val notificationJpaRepository: NotificationJpaRepository
) : NotificationRepository {

    override fun save(notification: Notification): Notification =
        notificationJpaRepository.save(notification)

    override fun saveAll(notifications: List<Notification>): List<Notification> =
        notificationJpaRepository.saveAll(notifications)

    override fun findByRecipient(recipientId: Long, cursor: Long?, size: Int): List<Notification> =
        notificationJpaRepository.findByRecipient(recipientId, cursor, PageRequest.of(0, size))

    @Transactional
    override fun markAsRead(recipientId: Long, notificationId: Long): Boolean =
        notificationJpaRepository.markAsRead(recipientId, notificationId) > 0

    @Transactional
    override fun markAllAsRead(recipientId: Long): Int =
        notificationJpaRepository.markAllAsRead(recipientId)
}
