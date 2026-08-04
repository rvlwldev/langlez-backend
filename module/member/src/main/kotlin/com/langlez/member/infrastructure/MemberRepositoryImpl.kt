package com.langlez.member.infrastructure

import com.langlez.core.cache.CacheProvider
import com.langlez.core.cache.get
import com.langlez.core.cache.getMany
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.QMember.Companion.member as qMember
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryImpl(
    private val em: EntityManager,
    private val dsl: JPAQueryFactory,
    caches: CacheProvider,
) : MemberRepository {

    private val members = caches.getCache("member")
    private val emails = caches.getCache("member-email")
    private val handles = caches.getCache("member-handle")
    private val providers = caches.getCache("member-provider")

    // id == 0 이면 아직 IDENTITY 채번 전인 신규 엔티티. em.persist가 즉시 INSERT하며 id를 채워준다.
    override fun save(member: Member): Member =
        (if (member.id == 0L) member.also(em::persist) else em.merge(member)).also(::updateCaches)

    override fun find(id: Long): Member? = members.get<Member>(id)
        ?: dsl.selectFrom(qMember).where(qMember.id.eq(id)).fetchOne()?.also(::updateCaches)

    override fun find(handle: String): Member? = handles.get<String>(handle)?.toLongOrNull()
        ?.let(::find)?.takeIf { it.handle == handle }
        ?: dsl.selectFrom(qMember).where(qMember.handle.eq(handle)).fetchOne()?.also(::updateCaches)

    override fun find(provider: Member.Provider, id: String): Member? = providers.get<String>("$provider:$id")
        ?.toLongOrNull()
        ?.let(::find)?.takeIf { it.provider == provider && it.providerId == id }
        ?: dsl.selectFrom(qMember)
            .where(qMember.provider.eq(provider), qMember.providerId.eq(id))
            .fetchOne()?.also(::updateCaches)

    override fun findByEmail(email: String): Member? = emails.get<String>(email)?.toLongOrNull()
        ?.let(::find)?.takeIf { it.email == email }
        ?: dsl.selectFrom(qMember).where(qMember.email.eq(email)).fetchOne()?.also(::updateCaches)

    override fun findAll(ids: Collection<Long>): List<Member> {
        if (ids.isEmpty()) return emptyList()
        val distinct = ids.toSet()

        val cached = members.getMany<Member>(distinct)
        val missing = distinct.filter { it !in cached }
        if (missing.isEmpty()) return cached.values.toList()

        val loaded = dsl.selectFrom(qMember).where(qMember.id.`in`(missing)).fetch()
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

        val loaded = dsl.selectFrom(qMember).where(qMember.handle.`in`(missing)).fetch()
        return cached + loaded.onEach(::updateCaches)
    }

    override fun findAll(size: Int, cursor: Long?): List<Member> {
        val query = dsl.selectFrom(qMember).orderBy(qMember.id.desc()).limit(size.toLong())
        if (cursor != null) query.where(qMember.id.lt(cursor))
        return query.fetch()
    }

    override fun count(): Long = dsl.select(qMember.count()).from(qMember).fetchOne() ?: 0L

    override fun delete(id: Long) {
        find(id)?.let(::delete)
    }

    /**
     * 캐시 무효화에 필요한 email/handle/provider 키는 멤버를 읽어야 알 수 있다.
     * DB 를 먼저 지우면 그 조회가 실패해 캐시에 고아 키가 영구히 남는다. 반드시 먼저 읽는다.
     */
    override fun delete(ids: List<Long>) = delete(findAll(ids))

    override fun delete(member: Member) {
        dsl.delete(qMember).where(qMember.id.eq(member.id)).execute()
        evictCaches(member)
    }

    override fun delete(members: Collection<Member>) {
        if (members.isEmpty()) return

        dsl.delete(qMember).where(qMember.id.`in`(members.map { it.id })).execute()
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
