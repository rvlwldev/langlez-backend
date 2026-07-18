package com.langlez.member.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

class MemberRequest {
    data class UpdateUsername(
        @field:NotBlank(message = "member.username.invalid")
        @field:Size(min = 3, max = 20, message = "member.username.invalid")
        val username: String,
    )

    data class UpdateNickname(
        @field:NotBlank(message = "member.nickname.invalid")
        @field:Size(min = 1, max = 20, message = "member.nickname.invalid")
        val nickname: String,
    )

    data class UpdateFcmToken(
        @field:NotBlank
        val token: String,
    )
}
