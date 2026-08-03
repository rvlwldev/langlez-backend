package com.langlez.member.api.request

import com.langlez.member.domain.Member
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern

data class MemberUpdateHandleRequest(
    @field:Schema(description = "새 handle (영문/숫자/._, 3~20자)", example = "user_123")
    @field:Pattern(regexp = Member.HANDLE_REGEX, message = "member.handle.invalid")
    val handle: String,
)
