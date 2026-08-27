package com.langlez.notification.api

import com.langlez.notification.api.response.NotificationResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Notification", description = "알림함 API")
interface NotificationAPI {

    @Operation(
        summary = "알림함 조회",
        description = "본인이 받은 알림만 최신순으로. cursor 는 직전 페이지 마지막 항목의 id 를 넣는다.",
    )
    fun list(
        memberId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 알림 id") cursor: Long?,
    ): List<NotificationResponse>

    @Operation(summary = "알림 읽음 처리", description = "본인이 받은 알림만 처리할 수 있다. 남의 알림이면 403 이다.")
    fun markRead(memberId: Long, @Parameter(description = "알림 id") id: Long)
}
