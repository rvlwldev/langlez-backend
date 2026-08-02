package com.langlez.member.infrastructure.jpa

import com.langlez.member.domain.Member
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: String): Member?
    fun findByUsername(username: String): Member?
    fun findAllByUsernameIn(usernames: Collection<String>): List<Member>
    fun findByProviderAndProviderId(provider: Member.Provider, providerId: String): Member?
    fun findByIdLessThanOrderByIdDesc(id: Long, pageable: Pageable): List<Member>
    fun findAllByOrderByIdDesc(pageable: Pageable): List<Member>
}
