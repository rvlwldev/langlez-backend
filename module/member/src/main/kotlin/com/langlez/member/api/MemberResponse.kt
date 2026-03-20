package com.langlez.member.api

import com.langlez.member.domain.Member
import java.time.Instant

class MemberResponse {
    data class Me(
        val email: String,
        val username: String,
        val nickname: String,
        val role: String,
        val isVerified: Boolean,
        val createdAt: Instant,
        val lastLoggedInAt: Instant?,
    ) {
        constructor(member: Member) : this(
            email = member.email,
            username = member.username,
            nickname = member.nickname,
            role = member.role.name,
            isVerified = member.isVerified,
            createdAt = member.createdAt,
            lastLoggedInAt = member.lastLoggedInAt,
        )
    }

    data class Public(
        val username: String,
        val nickname: String,
        val role: String,
    ) {
        constructor(member: Member) : this(
            username = member.username,
            nickname = member.nickname,
            role = member.role.name,
        )
    }
}
