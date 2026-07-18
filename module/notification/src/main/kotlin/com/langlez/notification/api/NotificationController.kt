package com.langlez.notification.api

import com.langlez.notification.application.NotificationService
import com.langlez.security.web.MemberID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val service: NotificationService
) {

    @GetMapping
    fun getNotifications(
        @MemberID memberId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): NotificationResponse.CursorList = service.getNotifications(memberId, cursor, size)

    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAsRead(
        @MemberID memberId: Long,
        @PathVariable notificationId: Long
    ) {
        service.markAsRead(memberId, notificationId)
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAllAsRead(
        @MemberID memberId: Long
    ) {
        service.markAllAsRead(memberId)
    }
}
