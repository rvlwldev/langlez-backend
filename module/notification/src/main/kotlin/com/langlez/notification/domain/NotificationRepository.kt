package com.langlez.notification.domain

interface NotificationRepository {
    fun save(notification: Notification): Notification

    fun find(id: Long): Notification?

    /** 최신순. 커서는 직전 페이지 마지막 알림의 id. */
    fun findAll(recipientId: Long, size: Int, cursor: Long?): List<Notification>
}
