package com.langlez.member.domain

interface MemberRepository {
    fun save(member: Member): Member

    fun findById(id: Long): Member?
    fun findByEmail(email: String): Member?
    fun findByUsername(username: String): Member?
    fun findByProvider(id: String, type: Member.Provider): Member?
    fun findByIds(ids: List<Long>): List<Member>
    fun countAll(): Long
    fun findAll(cursor: Long?, size: Int): List<Member>

    fun deleteAll(members: List<Member>)
}
