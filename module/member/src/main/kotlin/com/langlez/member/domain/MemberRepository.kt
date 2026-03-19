package com.langlez.member.domain

interface MemberRepository {
    fun save(member: Member): Member

    fun findById(id: Long): Member?
    fun findByEmail(email: String): Member?
    fun findByUsername(username: String): Member?
    fun findByProvider(id: String, type: MemberProvider.Type): Member?
    fun findByIds(ids: List<Long>): List<Member>

    fun deleteAll(members: List<Member>)
}
