package com.langlez.member.api.response

import com.langlez.member.domain.Member

data class MemberPublicResponse(
    val handle: String,
    val nickname: String,
    val role: String,
) {
    constructor(member: Member) : this(
        handle = member.handle,
        nickname = member.nickname,
        role = member.role.name,
    )
}
