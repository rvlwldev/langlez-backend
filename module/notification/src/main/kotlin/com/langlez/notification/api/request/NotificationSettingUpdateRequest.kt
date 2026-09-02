package com.langlez.notification.api.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalTime

/**
 * 전체 교체 요청이다. `mutedTypes` 에 부분 추가/삭제 개념은 없다 — 요청마다 전체 목록을 다시 보낸다.
 * `quietFrom`/`quietTo` 는 둘 다 있거나 둘 다 없어야 한다. 둘 다 없으면 방해금지 해제.
 */
data class NotificationSettingUpdateRequest(
    @field:Schema(description = "끌 알림 유형 전체 목록(교체)", example = "[\"CHAT_MESSAGE\", \"MEMBER_FOLLOWED\"]")
    val mutedTypes: Set<String> = emptySet(),

    @field:Schema(description = "방해금지 시작", example = "22:00", nullable = true)
    val quietFrom: LocalTime? = null,

    @field:Schema(description = "방해금지 종료", example = "07:00", nullable = true)
    val quietTo: LocalTime? = null,

    @field:Schema(description = "IANA 타임존. 없으면 방해금지가 적용되지 않는다", example = "Asia/Seoul", nullable = true)
    val timeZone: String? = null,
)
