package com.langlez.member.api.request

import jakarta.validation.constraints.NotBlank

data class MemberUpdateImageRequest(
    @field:NotBlank
    val key: String,
)
