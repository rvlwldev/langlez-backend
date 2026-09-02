package com.langlez.notification.api.request

import io.swagger.v3.oas.annotations.media.Schema

/** 전체 교체 요청이다. 개별 추가/삭제 개념은 없다 — 요청마다 끌 유형 전체 목록을 다시 보낸다. */
data class NotificationMuteUpdateRequest(
    @field:Schema(description = "끌 알림 유형 전체 목록(교체). 빈 배열이면 전부 켠다", example = "[\"CHAT_MESSAGE\", \"MEMBER_FOLLOWED\"]")
    val types: Set<String> = emptySet(),
)
