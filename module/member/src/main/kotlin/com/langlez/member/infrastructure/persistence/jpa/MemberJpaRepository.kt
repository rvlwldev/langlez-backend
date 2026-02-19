package com.langlez.member.infrastructure.persistence.jpa

import com.langlez.member.domain.Member

import com.langlez.member.domain.embedded.MemberProvider

import org.springframework.data.jpa.repository.EntityGraph

import org.springframework.data.jpa.repository.JpaRepository

import org.springframework.data.jpa.repository.Query

import java.time.Instant



interface MemberJpaRepository : JpaRepository<Member, Long> {

    @EntityGraph(attributePaths = ["images"])
    fun findByEmail(email: String): Member?

    fun findByUsername(username: String): Member?



    fun existsByUsername(username: String): Boolean



    @Query("SELECT m FROM Member m WHERE m.provider.id = :providerId AND m.provider.type = :providerType")

    fun findByProviderIdAndProviderType(providerId: String, providerType: MemberProvider.Type): Member?



    @Query("SELECT m FROM Member m WHERE m.isInitDone = false AND m.audit.createdAt < :threshold")

    fun findIncompleteOlderThan(threshold: Instant): List<Member>

}
