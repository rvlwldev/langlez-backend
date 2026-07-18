package com.langlez.member.application

import com.langlez.member.domain.Member

sealed class MemberCommand {
    data class Create(val email: String, val username: String? = null, val nickname: String)
    data class Provider(val id: String, val type: Member.Provider, val username: String)
}