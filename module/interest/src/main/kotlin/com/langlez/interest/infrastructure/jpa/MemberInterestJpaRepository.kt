package com.langlez.interest.infrastructure.jpa

import com.langlez.interest.domain.MemberInterest
import org.springframework.data.jpa.repository.JpaRepository

interface MemberInterestJpaRepository : JpaRepository<MemberInterest, Long> {
    fun findAllByMemberId(memberId: Long): List<MemberInterest>
    fun findAllByInterestId(interestId: Long): List<MemberInterest>
    fun deleteAllByMemberIdAndInterestIdIn(memberId: Long, interestIds: Collection<Long>)
}
