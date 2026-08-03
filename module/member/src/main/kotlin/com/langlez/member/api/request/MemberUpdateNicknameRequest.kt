package com.langlez.member.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class MemberUpdateNicknameRequest(
    @field:Schema(description = "새 닉네임 (1~20자)", example = "langlez유저")
    @field:NotBlank(message = "member.nickname.invalid")
    @field:Size(min = 1, max = 20, message = "member.nickname.invalid")
    val nickname: String,
)
