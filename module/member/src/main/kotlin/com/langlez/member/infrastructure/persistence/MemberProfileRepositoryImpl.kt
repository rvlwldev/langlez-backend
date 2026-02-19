package com.langlez.member.infrastructure.persistence

import com.langlez.member.domain.MemberProfile
import com.langlez.member.domain.MemberProfileRepository
import com.langlez.member.infrastructure.persistence.jpa.MemberProfileJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MemberProfileRepositoryImpl(private val jpa: MemberProfileJpaRepository) : MemberProfileRepository {
    override fun save(profile: MemberProfile): MemberProfile = jpa.save(profile)
    override fun findByMemberId(memberId: Long): MemberProfile? = jpa.findByIdOrNull(memberId)
    override fun delete(profile: MemberProfile) = jpa.delete(profile)
}
