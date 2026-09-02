package com.langlez.notification.api.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalTime

/**
 * 전체 교체 요청이다. `from`/`to` 는 둘 다 있거나 둘 다 없어야 한다(둘 다 없으면 방해금지 해제).
 * 부분 필드만 보내는 걸 지원하지 않는다 — mute 설정과 엔드포인트를 분리한 것과 같은 이유로,
 * 이 리소스 하나만 다루니 부분 갱신이라는 개념 자체가 필요 없다.
 */
data class NotificationQuietHoursUpdateRequest(
    @field:Schema(description = "방해금지 시작", example = "22:00", nullable = true)
    val from: LocalTime? = null,

    @field:Schema(description = "방해금지 종료", example = "07:00", nullable = true)
    val to: LocalTime? = null,

    @field:Schema(description = "IANA 타임존. 없으면 방해금지가 적용되지 않는다", example = "Asia/Seoul", nullable = true)
    val timeZone: String? = null,
)
