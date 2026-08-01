package com.langlez.member.application

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider

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

interface MemberRepository2 {
    fun save(member: Member): Member

    fun find(id: Long): Member?
    fun find(username: String): Member?
    fun find(provider: MemberProvider, id: String): Member?
    fun findByEmail(email: String): Member?
    fun findAll(ids: Collection<Long>): List<Member>

    // findAll(Collection<Long>) 과 소거 후 JVM 시그니처가 같아진다. 인터페이스라 @JvmName 도 못 쓴다.
    fun findAllByUsernames(usernames: Collection<String>): List<Member>
    fun findAll(size: Int, cursor: Long?): List<Member>
    fun count(): Long

    fun delete(id: Long)
    fun delete(ids: List<Long>)
    fun delete(member: Member)
    fun delete(members: Collection<Member>)
}