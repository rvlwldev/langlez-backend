package com.langlez.member.infrastructure

import com.langlez.core.cache.CacheProvider
import com.langlez.core.cache.get
import com.langlez.core.cache.getMany
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.Member
import com.langlez.member.infrastructure.jpa.MemberJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryImpl(
    private val jpa: MemberJpaRepository,
    caches: CacheProvider,
) : MemberRepository {

    private val members = caches.getCache("member")
    private val emails = caches.getCache("member-email")
    private val usernames = caches.getCache("member-username")
    private val providers = caches.getCache("member-provider")

    override fun save(member: Member): Member = jpa.save(member).also(::updateCaches)

    override fun find(id: Long): Member? = members.get<Member>(id) ?: jpa.findByIdOrNull(id)
        ?.also(::updateCaches)

    override fun find(username: String): Member? = usernames.get<String>(username)?.toLongOrNull()
        ?.let(::find)?.takeIf { it.username == username }
        ?: jpa.findByUsername(username)?.also(::updateCaches)

    override fun find(provider: Member.Provider, id: String): Member? = providers.get<String>("$provider:$id")
        ?.toLongOrNull()
        ?.let(::find)?.takeIf { it.provider == provider && it.providerId == id }
        ?: jpa.findByProviderAndProviderId(provider, id)?.also(::updateCaches)

    override fun findByEmail(email: String): Member? = emails.get<String>(email)?.toLongOrNull()
        ?.let(::find)?.takeIf { it.email == email }
        ?: jpa.findByEmail(email)?.also(::updateCaches)

    override fun findAll(ids: Collection<Long>): List<Member> {
        if (ids.isEmpty()) return emptyList()
        val distinct = ids.toSet()

        val cached = members.getMany<Member>(distinct)
        val missing = distinct.filter { it !in cached }
        if (missing.isEmpty()) return cached.values.toList()

        // 미스분은 본체 캐시만 채운다. 역인덱스는 단건 조회 시 채워진다.
        val loaded = jpa.findAllById(missing)
        members.putMany(loaded.associateBy { it.id })

        return cached.values + loaded
    }

    /**
     * 역인덱스 MGET 1회로 id 를 걷고, 본체 MGET 1회로 실제 값을 걷는다. 왕복 2 + DB 1.
     */
    override fun findAllByUsernames(usernames: Collection<String>): List<Member> {
        if (usernames.isEmpty()) return emptyList()
        val distinct = usernames.toSet()

        val ids = this.usernames.getMany<String>(distinct).values.mapNotNull(String::toLongOrNull)
        // 역인덱스가 낡아 다른 회원을 가리킬 수 있으므로 실제 username 으로 다시 거른다.
        val cached = members.getMany<Member>(ids).values.filter { it.username in distinct }
        val missing = distinct - cached.mapTo(mutableSetOf()) { it.username }
        if (missing.isEmpty()) return cached.toList()

        return cached + jpa.findAllByUsernameIn(missing).onEach(::updateCaches)
    }

    override fun findAll(size: Int, cursor: Long?): List<Member> {
        val pageable = PageRequest.of(0, size)

        return if (cursor == null) jpa.findAllByOrderByIdDesc(pageable)
        else jpa.findByIdLessThanOrderByIdDesc(cursor, pageable)
    }

    override fun count(): Long = jpa.count()

    override fun delete(id: Long) {
        find(id)?.let(::delete)
    }

    override fun delete(ids: List<Long>) = jpa.deleteAllById(ids).also { ids.forEach(::evictCaches) }
    override fun delete(member: Member) = jpa.delete(member).also { evictCaches(member) }
    override fun delete(members: Collection<Member>) = members.map { member -> member.id }.run(::delete)

    private fun updateCaches(member: Member) {
        val id = member.id.toString()
        members.put(member.id, member)
        emails.put(member.email, id)
        usernames.put(member.username, id)
        providers.put(with(member) { "$provider:$providerId" }, id)
    }

    private fun evictCaches(member: Member) {
        members.evict(member.id)
        emails.evict(member.email)
        usernames.evict(member.username)
        providers.evict(with(member) { "$provider:$providerId" })
    }

    private fun evictCaches(id: Long) = find(id)?.run(::evictCaches)

}
