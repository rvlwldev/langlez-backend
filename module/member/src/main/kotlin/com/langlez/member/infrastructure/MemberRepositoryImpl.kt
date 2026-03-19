package com.langlez.member.infrastructure

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRepository
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryImpl(
    private val jpa: MemberJpaRepository,
    private val cacheManager: CacheManager,
    private val redis: RedisTemplate<String, Any>
) : MemberRepository {

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["member"], key = "#member.id", condition = "#member.id != null"),
            CacheEvict(cacheNames = ["member_email"], key = "#member.email")
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
    override fun findByProvider(id: String, type: MemberProvider.Type): Member? =
        jpa.findByProviderIdAndProviderType(id, type)

    override fun findByIds(ids: List<Long>): List<Member> {
        if (ids.isEmpty()) return emptyList()

        val prefix = "member::"
        val results = mutableListOf<Member>()

        for (chunk in ids.chunked(500)) {
            val keys = chunk.map { "$prefix$it" }
            val vals = redis.opsForValue().multiGet(keys) ?: emptyList()

            val missedIds = mutableListOf<Long>()
            val membersToCache = mutableMapOf<String, Member>()

            // 캐시 조회
            chunk.forEachIndexed { i, id ->
                val cached = vals[i] as? Member

                if (cached == null) missedIds.add(id)// cache miss
                else results.add(cached)// cache hit
            }

            // DB 조회
            if (missedIds.isNotEmpty()) {
                val members = jpa.findAllById(missedIds)

                members.forEach { member ->
                    results.add(member)
                    membersToCache["$prefix${member.id}"] = member
                }

                if (membersToCache.isNotEmpty())
                    redis.opsForValue().multiSet(membersToCache)
            }
        }

        val sequences = results.associateBy { it.id }
        return ids.mapNotNull { sequences[it] }
    }

    @CacheEvict(cacheNames = ["member", "member_email", "member_username", "member_provider"], allEntries = true)
    override fun deleteAll(members: List<Member>) = jpa.deleteAll(members)
}

interface MemberJpaRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: String): Member?
    fun findByUsername(username: String): Member?
    fun findByProviderIdAndProviderType(providerId: String, providerType: MemberProvider.Type): Member?
}
