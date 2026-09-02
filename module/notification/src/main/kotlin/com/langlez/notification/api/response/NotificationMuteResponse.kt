package com.langlez.notification.api.response

import io.swagger.v3.oas.annotations.media.Schema

data class NotificationMuteResponse(
    @field:Schema(description = "꺼진 알림 유형") val mutedTypes: Set<String>,
)
