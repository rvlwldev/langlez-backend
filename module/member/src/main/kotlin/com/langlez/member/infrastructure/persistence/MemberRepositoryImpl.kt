package com.langlez.member.infrastructure.persistence

import com.langlez.member.domain.Member
import com.langlez.member.domain.repository.MemberRepository
import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryImpl(
    private val jpaMemberRepository: JpaMemberRepository,
) : MemberRepository {
    override fun save(member: Member): Member = jpaMemberRepository.save(member)

    override fun findByEmail(email: String): Member? = jpaMemberRepository.findByEmail(email)

    override fun findByProviderAndProviderId(
        provider: String,
        providerId: String,
    ): Member? = jpaMemberRepository.findByProviderAndProviderId(provider, providerId)
}
