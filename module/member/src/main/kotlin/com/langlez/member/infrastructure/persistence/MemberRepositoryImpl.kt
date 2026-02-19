package com.langlez.member.infrastructure.persistence

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.embedded.MemberProvider
import com.langlez.member.infrastructure.persistence.jpa.MemberJpaRepository
import java.time.Instant
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryImpl(private val jpa: MemberJpaRepository) : MemberRepository {

    override fun save(member: Member): Member =
        jpa.save(member)

    override fun findById(id: Long): Member? =
        jpa.findByIdOrNull(id)

    override fun findByEmail(email: String): Member? =
        jpa.findByEmail(email)

    override fun findByUsername(username: String): Member? =
        jpa.findByUsername(username)

    override fun existsByUsername(username: String): Boolean =
        jpa.existsByUsername(username)

    override fun findByProvider(id: String, type: MemberProvider.Type): Member? =
        jpa.findByProviderIdAndProviderType(id, type)

    override fun delete(member: Member) =
        jpa.delete(member)

    override fun deleteAll(members: List<Member>) =
        jpa.deleteAll(members)

    override fun findAllIncompleteOlderThan(threshold: Instant): List<Member> =
        jpa.findIncompleteOlderThan(threshold)
}
