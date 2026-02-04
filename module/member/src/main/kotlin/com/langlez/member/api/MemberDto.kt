package com.langlez.member.api

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRole

data class MemberResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val role: MemberRole,
) {
    companion object {
        fun from(member: Member): MemberResponse =
            MemberResponse(
                id = member.id!!,
                email = member.email,
                nickname = member.nickname,
                profileImageUrl = member.profileImageUrl,
                role = member.role,
            )
    }
}

data class UpdateMemberRequest(
    val nickname: String,
    val profileImageUrl: String?,
)
