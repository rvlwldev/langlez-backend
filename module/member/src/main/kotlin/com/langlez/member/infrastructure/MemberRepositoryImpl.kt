package com.langlez.member.infrastructure

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.infrastructure.jpa.MemberJpaRepository
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryImpl(
    private val jpa: MemberJpaRepository,
    private val cacheManager: CacheManager,
) : MemberRepository {

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["member"], key = "#member.id", condition = "#member.id != null"),
            CacheEvict(cacheNames = ["member_email"], key = "#member.email"),
            CacheEvict(cacheNames = ["member_username"], key = "#member.username"),
            CacheEvict(cacheNames = ["member_provider"], key = "#member.providerId + ':' + #member.provider"),
        ]
    )
    override fun save(member: Member): Member = jpa.save(member)

    @Cacheable(cacheNames = ["member"], key = "#id")
    override fun findById(id: Long): Member? = jpa.findByIdOrNull(id)

    @Cacheable(cacheNames = ["member_email"], key = "#email")
    override fun findByEmail(email: String): Member? = jpa.findByEmail(email)

    @Cacheable(cacheNames = ["member_username"], key = "#username")
    override fun findByUsername(username: String): Member? = jpa.findByUsername(username)

    @Cacheable(cacheNames = ["member_provider"], key = "#id + ':' + #type")
    override fun findByProvider(id: String, type: Member.Provider): Member? =
        jpa.findByProviderIdAndProvider(id, type)

    override fun findByIds(ids: List<Long>): List<Member> {
        if (ids.isEmpty()) return emptyList()
        return jpa.findAllById(ids)
    }

    override fun countAll(): Long = jpa.count()

    override fun findAll(cursor: Long?, size: Int): List<Member> {
        val pageable = org.springframework.data.domain.PageRequest.of(0, size)
        return if (cursor == null) {
            jpa.findAllByOrderByIdDesc(pageable)
        } else {
            jpa.findByIdLessThanOrderByIdDesc(cursor, pageable)
        }
    }

    @CacheEvict(cacheNames = ["member", "member_email", "member_username", "member_provider"], allEntries = true)
    override fun deleteAll(members: List<Member>) = jpa.deleteAll(members)
}
