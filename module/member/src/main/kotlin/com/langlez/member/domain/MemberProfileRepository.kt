package com.langlez.member.domain

interface MemberProfileRepository {
    fun save(profile: MemberProfile): MemberProfile
    fun findByMemberId(memberId: Long): MemberProfile?
    fun delete(profile: MemberProfile)
}
