package com.langlez.member.domain

interface MemberRepository {
    fun save(member: Member): Member

    fun find(id: Long): Member?
    fun find(handle: String): Member?
    fun find(provider: Member.Provider, id: String): Member?
    fun findByEmail(email: String): Member?

    fun findAll(ids: Collection<Long>): List<Member>
    fun findAll(size: Int, cursor: Long?): List<Member>
    fun findAllByHandles(handles: Collection<String>): List<Member>
    fun count(): Long

    fun delete(id: Long)
    fun delete(ids: List<Long>)
    fun delete(member: Member)
    fun delete(members: Collection<Member>)
}