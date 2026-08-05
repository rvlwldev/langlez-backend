package com.langlez.member.infrastructure

import com.langlez.core.cache.CacheProvider
import com.langlez.core.cache.get
import com.langlez.core.cache.getMany
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.infrastructure.jpa.MemberJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import com.langlez.member.domain.QMember.Companion.member as QMember

@Repository
class MemberRepositoryImpl(
    private val jpa: MemberJpaRepository,
    private val dsl: JPAQueryFactory,
    caches: CacheProvider,
) : MemberRepository {

    private val members = caches.getCache("member")
    private val emails = caches.getCache("member-email")
    private val handles = caches.getCache("member-handle")
    private val providers = caches.getCache("member-provider")

    override fun save(member: Member): Member = jpa.save(member).also(::updateCaches)

    override fun find(id: Long): Member? {
        val member = members.get<Member>(id) ?: jpa.findByIdOrNull(id)
        return member?.also(::updateCaches)
    }

    override fun find(handle: String): Member? {
        val id = handles.get<String>(handle)?.toLongOrNull()

        val member = if (id != null) jpa.findByIdOrNull(id)
        else dsl.selectFrom(QMember).where(QMember.handle.eq(handle)).fetchOne()

        return member?.also(::updateCaches)
    }

    override fun find(provider: Member.Provider, id: String): Member? {
        val memberId = providers.get<String>("$provider:$id")?.toLongOrNull()

        val member = if (memberId != null) jpa.findByIdOrNull(memberId)
        else dsl.selectFrom(QMember)
            .where(QMember.provider.eq(provider), QMember.providerId.eq(id))
            .fetchOne()

        return member?.also(::updateCaches)
    }

    override fun findByEmail(email: String): Member? {
        val id = emails.get<String>(email)?.toLongOrNull()

        val member = if (id != null) jpa.findByIdOrNull(id)
        else dsl.selectFrom(QMember).where(QMember.email.eq(email)).fetchOne()

        return member?.also(::updateCaches)
    }

    override fun findAll(ids: Collection<Long>): List<Member> {
        if (ids.isEmpty()) return emptyList()
        val distinct = ids.toSet()

        val cached = members.getMany<Member>(distinct)
        val missing = distinct.filter { it !in cached }
        if (missing.isEmpty()) return cached.values.toList()

        val loaded = dsl.selectFrom(QMember).where(QMember.id.`in`(missing)).fetch()
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

        val loaded = dsl.selectFrom(QMember).where(QMember.handle.`in`(missing)).fetch()
        return cached + loaded.onEach(::updateCaches)
    }

    override fun findAll(size: Int, cursor: Long?): List<Member> {
        val query = dsl.selectFrom(QMember).orderBy(QMember.id.desc()).limit(size.toLong())
        if (cursor != null) query.where(QMember.id.lt(cursor))
        return query.fetch()
    }

    override fun count(): Long = jpa.count()

    override fun delete(id: Long) {
        find(id)?.let(::delete)
    }

    override fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        delete(findAll(ids))
    }

    override fun delete(member: Member) {
        jpa.delete(member)
        evictCaches(member)
    }

    override fun delete(members: Collection<Member>) {
        if (members.isEmpty()) return
        jpa.deleteAllInBatch(members)
        members.forEach(::evictCaches)
    }

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

}
