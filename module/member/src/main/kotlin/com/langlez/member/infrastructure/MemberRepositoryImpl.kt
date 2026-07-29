package com.langlez.member.infrastructure

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.MemberProvider
import com.langlez.member.infrastructure.jpa.MemberJpaRepository
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryImpl(
    private val jpa: MemberJpaRepository,
    private val cacheManager: CacheManager
) : MemberRepository {

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["member"], key = "#member.id", condition = "#member.id != null"),
            CacheEvict(cacheNames = ["member-email"], key = "#member.email"),
            CacheEvict(cacheNames = ["member-provider"], key = "#member.provider + ':' + #member.providerId"),
        ]
    )
    override fun save(member: Member): Member = jpa.save(member)

    @Cacheable(cacheNames = ["member"], key = "#id")
    override fun findById(id: Long): Member? = jpa.findByIdOrNull(id)

    override fun findByIds(ids: List<Long>): List<Member> = if (ids.isEmpty()) emptyList() else jpa.findAllById(ids)

    @Cacheable(cacheNames = ["member-email"], key = "#email")
    override fun findByEmail(email: String): Member? = jpa.findByEmail(email)

    @Cacheable(cacheNames = ["member-provider"], key = "#type + ':' + #id")
    override fun findByProvider(type: MemberProvider, id: String): Member? = jpa.findByProviderIdAndProvider(id, type)

    @Cacheable(cacheNames = ["member-username"], key = "#username")
    override fun findByUsername(username: String): Member? = jpa.findByUsername(username)

    override fun findByUsernames(usernames: List<String>): List<Member> {
        if (usernames.isEmpty()) return emptyList()

        val names = usernames.toSet()

        val cache = cacheManager.getCache("member-username") ?: ConcurrentMapCache("member-username")
        val cached = names.mapNotNull { cache.get(it, Member::class.java) }
        if (cached.size == names.size) return cached

        val cachedNames = cached.map { it.username }.toSet()
        val misses = cachedNames.filterNot { names.contains(it) }
        val fetches = if (misses.isNotEmpty()) jpa.findAllByUsernameIn(misses) else emptyList()
        fetches.forEach { cache.put(it.username, it) }

        return cached + fetches
    }

    override fun findAll(size: Int, cursor: Long?): List<Member> {
        val pageable = PageRequest.of(0, size)

        return if (cursor == null) jpa.findAllByOrderByIdDesc(pageable)
        else jpa.findByIdLessThanOrderByIdDesc(cursor, pageable)
    }

    override fun countAll(): Long = jpa.count()

    @CacheEvict(cacheNames = ["member", "member-email", "member-username", "member-provider"], allEntries = true)
    override fun deleteAll(members: List<Member>) = jpa.deleteAll(members)
}
