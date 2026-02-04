package com.langlez.member.infrastructure.persistence

import com.langlez.member.domain.Member
import org.springframework.data.jpa.repository.JpaRepository

interface JpaMemberRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: String): Member?

    fun findByProviderAndProviderId(
        provider: String,
        providerId: String,
    ): Member?
}
