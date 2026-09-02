package com.langlez.notification.api

import com.langlez.notification.api.request.NotificationSettingUpdateRequest
import com.langlez.notification.api.response.NotificationResponse
import com.langlez.notification.api.response.NotificationSettingResponse
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

    @Operation(summary = "알림 수신 설정 조회", description = "끈 알림 유형과 방해금지 시간대. 설정한 적 없으면 전부 켠 상태로 나온다.")
    fun getSettings(memberId: Long): NotificationSettingResponse

    @Operation(
        summary = "알림 수신 설정 변경",
        description = "mutedTypes 는 전체 교체다. quietFrom/quietTo 는 둘 다 있거나 둘 다 없어야 한다.",
    )
    fun updateSettings(memberId: Long, request: NotificationSettingUpdateRequest): NotificationSettingResponse
}
