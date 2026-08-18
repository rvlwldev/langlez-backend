package com.langlez.member.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class MemberUpdateFcmTokenRequest(
    @field:Schema(description = "FCM(Firebase Cloud Messaging) 디바이스 토큰")
    @field:NotBlank
    val token: String,
)
