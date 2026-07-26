package com.langlez.interest.infrastructure

import com.langlez.interest.domain.MemberInterest
import com.langlez.interest.domain.MemberInterestRepository
import com.langlez.interest.infrastructure.jpa.MemberInterestJpaRepository
import org.springframework.stereotype.Repository

@Repository
class MemberInterestRepositoryImpl(
    private val jpa: MemberInterestJpaRepository,
) : MemberInterestRepository {
    override fun findByMemberId(memberId: Long): List<MemberInterest> = jpa.findAllByMemberId(memberId)
    override fun findByInterestId(interestId: Long): List<MemberInterest> = jpa.findAllByInterestId(interestId)
    override fun saveAll(list: List<MemberInterest>): List<MemberInterest> = jpa.saveAll(list)
    override fun deleteAll(list: List<MemberInterest>) = jpa.deleteAll(list)
}
