package com.langlez.member.api.request

import com.langlez.member.domain.Member
import jakarta.validation.constraints.Pattern

data class MemberUpdateHandleRequest(
    @field:Pattern(regexp = Member.HANDLE_REGEX, message = "member.handle.invalid")
    val handle: String,
)
