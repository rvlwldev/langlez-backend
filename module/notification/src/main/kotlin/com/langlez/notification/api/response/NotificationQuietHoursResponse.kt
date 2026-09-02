package com.langlez.notification.api.response

import com.langlez.notification.domain.NotificationSetting
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalTime

data class NotificationQuietHoursResponse(
    @field:Schema(description = "방해금지 시작", nullable = true) val from: LocalTime?,
    @field:Schema(description = "방해금지 종료", nullable = true) val to: LocalTime?,
    @field:Schema(description = "IANA 타임존", nullable = true) val timeZone: String?,
) {
    constructor(setting: NotificationSetting) : this(
        from = setting.quietFrom,
        to = setting.quietTo,
        timeZone = setting.timeZone,
    )
}
