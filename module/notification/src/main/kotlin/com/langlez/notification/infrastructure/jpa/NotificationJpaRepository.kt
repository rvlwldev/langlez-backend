package com.langlez.notification.infrastructure.jpa

import com.langlez.notification.domain.Notification
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface NotificationJpaRepository : JpaRepository<Notification, Long> {
    @Query("SELECT n FROM Notification n WHERE n.recipientId = :recipientId AND (:cursor IS NULL OR n.id < :cursor) ORDER BY n.id DESC")
    fun findByRecipient(recipientId: Long, cursor: Long?, pageable: Pageable): List<Notification>

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientId = :recipientId AND n.id = :notificationId")
    fun markAsRead(recipientId: Long, notificationId: Long): Int

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientId = :recipientId AND n.read = false")
    fun markAllAsRead(recipientId: Long): Int
}
