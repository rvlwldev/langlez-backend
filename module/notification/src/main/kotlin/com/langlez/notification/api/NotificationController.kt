package com.langlez.notification.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.annotation.MemberId
import com.langlez.notification.api.request.NotificationMuteUpdateRequest
import com.langlez.notification.api.request.NotificationQuietHoursUpdateRequest
import com.langlez.notification.api.response.NotificationMuteResponse
import com.langlez.notification.api.response.NotificationQuietHoursResponse
import com.langlez.notification.api.response.NotificationResponse
import com.langlez.notification.api.response.NotificationSettingResponse
import com.langlez.notification.application.NotificationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val service: NotificationService,
    private val mapper: ObjectMapper,
) : NotificationAPI {

    @GetMapping
    override fun list(
        @MemberId memberId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<NotificationResponse> =
        service.list(memberId, size.coerceIn(1, MAX_SIZE), cursor).map { NotificationResponse(it, mapper) }

    @PatchMapping("/{id}/read")
    @ResponseStatus(NO_CONTENT)
    override fun markRead(@MemberId memberId: Long, @PathVariable id: Long) {
        service.markRead(memberId, id)
    }

    @GetMapping("/settings")
    override fun getSettings(@MemberId memberId: Long): NotificationSettingResponse =
        NotificationSettingResponse(service.settingsOf(memberId))

    @PutMapping("/mutes")
    override fun updateMutes(
        @MemberId memberId: Long,
        @RequestBody @Valid request: NotificationMuteUpdateRequest,
    ): NotificationMuteResponse = NotificationMuteResponse(service.updateMutes(memberId, request.types))

    @PutMapping("/quiet-hours")
    override fun updateQuietHours(
        @MemberId memberId: Long,
        @RequestBody @Valid request: NotificationQuietHoursUpdateRequest,
    ): NotificationQuietHoursResponse = NotificationQuietHoursResponse(
        service.updateQuietHours(memberId, request.from, request.to, request.timeZone)
    )

    companion object {
        private const val DEFAULT_SIZE = 20

        // 상한이 없으면 size=1000000 한 방으로 알림 이력을 통째로 긁어갈 수 있다.
        private const val MAX_SIZE = 50
    }
}
