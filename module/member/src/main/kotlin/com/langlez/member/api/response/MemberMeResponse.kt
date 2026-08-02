package com.langlez.member.api.response

import com.langlez.member.domain.Member
import java.time.Instant

data class MemberMeResponse(
    val email: String,
    val username: String,
    val nickname: String,
    val role: String,
    val createdAt: Instant,
    val lastAccessedAt: Instant?,
) {
    constructor(member: Member) : this(
        email = member.email,
        username = member.username,
        nickname = member.nickname,
        role = member.role.name,
        createdAt = member.createdAt,
        lastAccessedAt = member.audit.lastAccessedAt,
    )
}
