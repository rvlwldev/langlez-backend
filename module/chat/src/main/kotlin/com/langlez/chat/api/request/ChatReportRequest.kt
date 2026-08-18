package com.langlez.chat.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChatReportRequest(
    @field:Schema(description = "신고 사유")
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,

    @field:Schema(description = "문제가 된 메시지 id", nullable = true)
    val triggerMessageId: String? = null,
)
