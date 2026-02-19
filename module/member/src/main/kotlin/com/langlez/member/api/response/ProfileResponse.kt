package com.langlez.member.api.response

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProfile
import com.langlez.member.domain.embedded.MemberIntroduction
import com.langlez.member.domain.embedded.MemberLanguage
import com.langlez.member.domain.embedded.MemberLocation
import com.langlez.member.domain.embedded.MemberPersonality
import java.time.Instant

/** Member 전체 프로필 응답 DTO */
data class ProfileResponse(
    val id: Long,
    val username: String?,
    val email: String,
    val nickname: String,
    val role: Member.Role,
    val isInitDone: Boolean,

    // Profile Details
    val introduction: MemberIntroduction?,
    val personality: MemberPersonality?,
    val location: MemberLocation?,
    val languages: Set<MemberLanguage>?,
    val images: List<String>, // 이미지 URL 목록

    // Audit
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(member: Member, profile: MemberProfile?): ProfileResponse =
            ProfileResponse(
                id = member.id,
                username = member.username,
                email = member.email,
                nickname = member.nickname,
                role = member.role,
                isInitDone = member.isInitDone,
                introduction = profile?.introduction,
                personality = profile?.personality,
                location = profile?.location,
                languages = profile?.languages,
                images = member.images.sortedBy { it.sequence }.map { it.url },
                lastLoginAt = member.audit.lastLoggedInAt,
                createdAt = member.audit.createdAt,
                updatedAt = member.audit.updatedAt,
            )
    }
}
