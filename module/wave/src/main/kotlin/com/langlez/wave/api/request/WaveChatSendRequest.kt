package com.langlez.wave.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class WaveChatSendRequest(
    @field:Schema(description = "본문. 저장되지 않고 방이 끝나면 사라진다")
    @field:NotBlank(message = "wave.chat.empty")
    @field:Size(max = 1000)
    val content: String,
)
