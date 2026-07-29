package com.langlez.member.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class MemberUpdateNicknameRequest(
    @field:NotBlank(message = "member.nickname.invalid")
    @field:Size(min = 1, max = 20, message = "member.nickname.invalid")
    val nickname: String,
)
