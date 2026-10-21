package com.langlez.member2.infrastructure

import com.langlez.core.cache.CacheProvider
import com.langlez.member2.domain.Member
import com.langlez.member2.domain.MemberAccessLog
import com.langlez.member2.domain.MemberAudit
import com.langlez.member2.domain.MemberProvider
import com.langlez.member2.infrastructure.jpa.MemberJpa
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class MemberRepository(
    private val jpa: MemberJpa,
    private val dsl: JPAQueryFactory,
    cacheProvider: CacheProvider
) {

    private val caches = cacheProvider.getCache("members")

    fun save(member: Member) {}
    fun save(accessLog: MemberAccessLog) {}
    fun save(audit: MemberAudit) {}
    fun save(provider: MemberProvider) {}

    // TODO : 조회 갱신 ...

}

