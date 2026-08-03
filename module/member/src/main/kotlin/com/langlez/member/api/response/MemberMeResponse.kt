package com.langlez.member.api.response

import com.langlez.member.domain.Member
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class MemberMeResponse(
    @field:Schema(description = "이메일") val email: String,
    @field:Schema(description = "handle(고유 아이디)") val handle: String,
    @field:Schema(description = "닉네임") val nickname: String,
    @field:Schema(description = "권한", example = "MEMBER") val role: String,
    @field:Schema(description = "프로필 이미지 URL", nullable = true) val imageUrl: String?,
    @field:Schema(description = "가입일시") val createdAt: Instant,
    @field:Schema(description = "마지막 접속일시", nullable = true) val lastAccessedAt: Instant?,
) {
    constructor(member: Member) : this(
        email = member.email,
        handle = member.handle,
        nickname = member.nickname,
        role = member.role.name,
        imageUrl = member.imageUrl,
        createdAt = member.createdAt,
        lastAccessedAt = member.audit.lastAccessedAt,
    )
}
