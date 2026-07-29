package com.langlez.member.api.request

import com.langlez.member.domain.Member
import jakarta.validation.constraints.Pattern

data class MemberUpdateUsernameRequest(
    @field:Pattern(regexp = Member.USERNAME_REGEX, message = "member.username.invalid")
    val username: String,
)
