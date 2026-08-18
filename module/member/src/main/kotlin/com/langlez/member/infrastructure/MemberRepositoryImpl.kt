package com.langlez.member.infrastructure

import com.langlez.core.cache.CacheProvider
import com.langlez.core.cache.get
import com.langlez.core.cache.getMany
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.infrastructure.jpa.MemberJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
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
        val member = members.get<Member>(id) ?: jpa.findWithAuditById(id)
        return member?.also(::updateCaches)
    }

    /**
     * handle 은 바뀔 수 있는 키라 캐시에 구 handle 이 TTL 까지 남는다.
     * 캐시로 찾은 회원의 handle 이 요청한 값과 다르면 그 항목은 이미 낡은 것이므로
     * 버리고 DB 로 간다. 이 검증이 없으면 핸들을 바꾼 뒤 구 핸들로 조회했을 때
     * 더는 그 핸들을 쓰지 않는 회원이 반환된다.
     */
    override fun find(handle: String): Member? {
        val id = handles.get<String>(handle)?.toLongOrNull()
        val cached = id?.let(jpa::findWithAuditById)?.takeIf { it.handle == handle }

        if (cached == null && id != null) handles.evict(handle)

        val member = cached
            ?: dsl.selectFrom(QMember).leftJoin(QMember.audit).fetchJoin()
                .where(QMember.handle.eq(handle)).fetchOne()

        return member?.also(::updateCaches)
    }

    override fun find(provider: Member.Provider, id: String): Member? {
        val memberId = providers.get<String>("$provider:$id")?.toLongOrNull()

        val member = if (memberId != null) jpa.findWithAuditById(memberId)
        else dsl.selectFrom(QMember).leftJoin(QMember.audit).fetchJoin()
            .where(QMember.provider.eq(provider), QMember.providerId.eq(id))
            .fetchOne()

        return member?.also(::updateCaches)
    }

    override fun findByEmail(email: String): Member? {
        val id = emails.get<String>(email)?.toLongOrNull()

        val member = if (id != null) jpa.findWithAuditById(id)
        else dsl.selectFrom(QMember).leftJoin(QMember.audit).fetchJoin()
            .where(QMember.email.eq(email)).fetchOne()

        return member?.also(::updateCaches)
    }

    override fun findAll(ids: Collection<Long>): List<Member> {
        if (ids.isEmpty()) return emptyList()
        val distinct = ids.toSet()

        val cached = members.getMany<Member>(distinct)
        val missing = distinct.filter { it !in cached }
        if (missing.isEmpty()) return cached.values.toList()

        val loaded = dsl.selectFrom(QMember).leftJoin(QMember.audit).fetchJoin().where(QMember.id.`in`(missing)).fetch()
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

        val loaded = dsl.selectFrom(QMember).leftJoin(QMember.audit).fetchJoin().where(QMember.handle.`in`(missing)).fetch()
        return cached + loaded.onEach(::updateCaches)
    }

    override fun findAll(size: Int, cursor: Long?): List<Member> {
        val query = dsl.selectFrom(QMember).leftJoin(QMember.audit).fetchJoin()
            .orderBy(QMember.id.desc()).limit(size.toLong())
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
        // deleteAllInBatch 를 쓰면 안 된다. 영속성 컨텍스트를 우회해 Member.audit 의
        // cascade/orphanRemoval 이 돌지 않고 member_audits 고아 행이 남는다.
        jpa.deleteAll(members)
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
