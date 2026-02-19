package com.langlez.member.api.response

import com.langlez.member.domain.Member

/** Member 요약 정보 응답 DTO (목록 조회 등) */
data class MemberResponse(
    val id: Long,
    val username: String?,
    val nickname: String,
    val email: String,
    val role: Member.Role,
    val profileImageUrl: String? // 대표 이미지 URL
) {
    companion object {
        fun from(member: Member): MemberResponse =
            MemberResponse(
                id = member.id,
                username = member.username,
                nickname = member.nickname,
                email = member.email,
                role = member.role,
                profileImageUrl = member.images.firstOrNull { it.represent }?.url ?: member.images.firstOrNull()?.url
            )
    }
}
