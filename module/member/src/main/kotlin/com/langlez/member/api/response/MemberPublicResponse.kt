package com.langlez.member.api.response

import com.langlez.member.domain.Member
import io.swagger.v3.oas.annotations.media.Schema

data class MemberPublicResponse(
    @field:Schema(description = "handle(고유 아이디)") val handle: String,
    @field:Schema(description = "닉네임") val nickname: String,
    @field:Schema(description = "권한", example = "MEMBER") val role: String,
) {
    constructor(member: Member) : this(
        handle = member.handle,
        nickname = member.nickname,
        role = member.role.name,
    )
}
