package com.langlez.notification.api.response

import com.langlez.notification.application.NotificationSettingSnapshot
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalTime

data class NotificationSettingResponse(
    @field:Schema(description = "꺼진 알림 유형") val mutedTypes: Set<String>,
    @field:Schema(description = "방해금지 시작", nullable = true) val quietFrom: LocalTime?,
    @field:Schema(description = "방해금지 종료", nullable = true) val quietTo: LocalTime?,
    @field:Schema(description = "IANA 타임존", nullable = true) val timeZone: String?,
) {
    constructor(snapshot: NotificationSettingSnapshot) : this(
        mutedTypes = snapshot.mutedTypes,
        quietFrom = snapshot.quietFrom,
        quietTo = snapshot.quietTo,
        timeZone = snapshot.timeZone,
    )
}
