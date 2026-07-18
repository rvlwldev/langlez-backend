package com.langlez.notification.api

import java.time.Instant

class NotificationResponse {
    data class NotificationDto(
        val notificationId: Long,
        val type: String,
        val title: String,
        val body: String,
        val read: Boolean,
        val createdAt: Instant
    )

    data class CursorList(
        val nextCursor: Long?,
        val notifications: List<NotificationDto>
    )
}
