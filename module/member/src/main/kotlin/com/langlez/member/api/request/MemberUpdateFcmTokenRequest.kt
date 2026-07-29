package com.langlez.member.api.request

import jakarta.validation.constraints.NotBlank

data class MemberUpdateFcmTokenRequest(
    @field:NotBlank
    val token: String,
)
