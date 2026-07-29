package com.langlez.member.api.response

import com.langlez.member.domain.Member

data class MemberPublicResponse(
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
