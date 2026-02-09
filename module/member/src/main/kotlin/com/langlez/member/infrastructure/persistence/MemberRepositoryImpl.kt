package com.langlez.member.infrastructure.persistence

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.embedded.MemberProvider
import java.time.Instant
import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryImpl(private val jpaMemberRepository: JpaMemberRepository) :
        MemberRepository {

        override fun save(member: Member): Member = jpaMemberRepository.save(member)

        override fun findById(id: Long): Member? = jpaMemberRepository.findById(id).orElse(null)

        override fun findByEmail(email: String): Member? = jpaMemberRepository.findByEmail(email)

        override fun findByHandle(handle: String): Member? =
                jpaMemberRepository.findByHandle(handle)

        override fun existsByHandle(handle: String): Boolean =
                jpaMemberRepository.existsByHandle(handle)

        override fun findByProvider(
                providerId: String,
                providerType: MemberProvider.Type
        ): Member? = jpaMemberRepository.findByProviderIdAndProviderType(providerId, providerType)

        override fun delete(member: Member) = jpaMemberRepository.delete(member)

        override fun deleteAll(members: List<Member>) = jpaMemberRepository.deleteAll(members)

        override fun findIncompleteOlderThan(threshold: Instant): List<Member> =
                jpaMemberRepository.findIncompleteOlderThan(threshold)
}
