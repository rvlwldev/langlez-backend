package com.langlez.member.api.response

import com.langlez.member.domain.Member
import com.langlez.member.domain.embedded.MemberPersonality
import java.time.Instant
import java.time.LocalDate

/** Member API 응답 DTO */
data class MemberResponseV1(
        val id: Long,
        val handle: String?,
        val email: String,
        val nickname: String,
        val role: Member.Role,
        val init: Boolean,

        // Introduction
        val bio: String?,
        val goal: String?,
        val want: String?,

        // Personality
        val nationality: String?,
        val birthDay: LocalDate?,
        val gender: MemberPersonality.Gender?,
        val mbti: MemberPersonality.MBTI?,

        // Location
        val address: String?,

        // Audit
        val lastLoginAt: Instant?,
        val createdAt: Instant,
        val updatedAt: Instant,
) {
        companion object {
                fun from(member: Member): MemberResponseV1 =
                        MemberResponseV1(
                                id = member.id,
                                handle = member.handle,
                                email = member.email,
                                nickname = member.nickname,
                                role = member.role,
                                init = member.init,
                                bio = member.introduction?.bio,
                                goal = member.introduction?.goal,
                                want = member.introduction?.want,
                                nationality = member.personality?.nationality?.code,
                                birthDay = member.personality?.birthDay,
                                gender = member.personality?.gender,
                                mbti = member.personality?.mbti,
                                address = member.location?.address,
                                lastLoginAt = member.audit.lastLoggedInAt,
                                createdAt = member.audit.createdAt,
                                updatedAt = member.audit.updatedAt,
                        )
        }
}
