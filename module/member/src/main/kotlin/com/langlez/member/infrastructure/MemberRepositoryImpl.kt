package com.langlez.member.infrastructure

import com.langlez.core.cache.CacheProvider
import com.langlez.core.cache.get
import com.langlez.core.cache.getMany
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
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
    private val handles = caches.getCache("member-handle")
    private val providers = caches.getCache("member-provider")

    override fun save(member: Member): Member = jpa.save(member).also(::updateCaches)

    override fun find(id: Long): Member? = members.get<Member>(id) ?: jpa.findByIdOrNull(id)
        ?.also(::updateCaches)

    override fun find(handle: String): Member? = handles.get<String>(handle)?.toLongOrNull()
        ?.let(::find)?.takeIf { it.handle == handle }
        ?: jpa.findByHandle(handle)?.also(::updateCaches)

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

        val loaded = jpa.findAllById(missing)
        members.putMany(loaded.associateBy { it.id })

        return cached.values + loaded
    }

    override fun findAllByHandles(handles: Collection<String>): List<Member> {
        if (handles.isEmpty()) return emptyList()
        val distinct = handles.toSet()

        val ids = this.handles.getMany<String>(distinct).values.mapNotNull(String::toLongOrNull)
        val cached = members.getMany<Member>(ids).values.filter { it.handle in distinct }
        val missing = distinct - cached.mapTo(mutableSetOf()) { it.handle }
        if (missing.isEmpty()) return cached.toList()

        return cached + jpa.findAllByHandleIn(missing).onEach(::updateCaches)
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
        handles.put(member.handle, id)
        providers.put(with(member) { "$provider:$providerId" }, id)
    }

    private fun evictCaches(member: Member) {
        members.evict(member.id)
        emails.evict(member.email)
        handles.evict(member.handle)
        providers.evict(with(member) { "$provider:$providerId" })
    }

    private fun evictCaches(id: Long) = find(id)?.run(::evictCaches)
}
