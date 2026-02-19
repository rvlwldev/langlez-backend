package com.langlez.member.domain

import com.langlez.member.domain.embedded.MemberProvider
import java.time.Instant

interface MemberRepository {
    fun save(member: Member): Member
    fun findById(id: Long): Member?
    fun findByEmail(email: String): Member?
    fun findByUsername(username: String): Member?
    fun findByProvider(id: String, type: MemberProvider.Type): Member?
    fun existsByUsername(username: String): Boolean
    fun delete(member: Member)
    fun deleteAll(members: List<Member>)
    fun findAllIncompleteOlderThan(threshold: Instant): List<Member>
}
