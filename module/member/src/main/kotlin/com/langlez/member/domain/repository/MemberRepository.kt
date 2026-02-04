package com.langlez.member.domain.repository

import com.langlez.member.domain.Member

interface MemberRepository {
    fun save(member: Member): Member

    fun findByEmail(email: String): Member?

    fun findByProviderAndProviderId(
        provider: String,
        providerId: String,
    ): Member?
}
