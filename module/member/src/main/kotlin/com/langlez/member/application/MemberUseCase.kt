package com.langlez.member.application

import com.langlez.member.domain.Member

data class CreateMemberCommand(
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val provider: String,
    val providerId: String,
)

data class UpdateMemberCommand(
    val nickname: String,
    val profileImageUrl: String?,
)

interface MemberUseCase {
    fun findOrCreateMember(command: CreateMemberCommand): Member
}
