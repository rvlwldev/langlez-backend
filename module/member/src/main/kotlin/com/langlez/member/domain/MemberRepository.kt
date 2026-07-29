package com.langlez.member.domain

interface MemberRepository {
    fun save(member: Member): Member

    fun findById(id: Long): Member?
    fun findByIds(ids: List<Long>): List<Member>
    fun findByEmail(email: String): Member?
    fun findByUsername(username: String): Member?
    fun findByUsernames(usernames: List<String>): List<Member>
    fun findByProvider(type: MemberProvider, id: String): Member?
    fun findAll(size: Int, cursor: Long?): List<Member>

    fun countAll(): Long

    fun deleteAll(members: List<Member>)
}
