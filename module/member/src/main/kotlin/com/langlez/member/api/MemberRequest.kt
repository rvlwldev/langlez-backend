package com.langlez.member.api

import com.langlez.member.domain.Member
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

class MemberRequest {
    data class UpdateUsername(
        @field:Pattern(regexp = Member.USERNAME_REGEX, message = "member.username.invalid")
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
