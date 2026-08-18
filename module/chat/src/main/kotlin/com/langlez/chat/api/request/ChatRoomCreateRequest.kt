package com.langlez.chat.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive

data class ChatRoomCreateRequest(
    @field:Schema(description = "대화 상대 회원 id", example = "42")
    @field:Positive
    val partnerId: Long,
)
