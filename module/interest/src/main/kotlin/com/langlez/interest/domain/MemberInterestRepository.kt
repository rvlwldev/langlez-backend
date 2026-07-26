package com.langlez.interest.domain

interface MemberInterestRepository {
    fun findByMemberId(memberId: Long): List<MemberInterest>
    fun findByInterestId(interestId: Long): List<MemberInterest>
    fun saveAll(list: List<MemberInterest>): List<MemberInterest>
    fun deleteAll(list: List<MemberInterest>)
}
