package com.langlez.member.infrastructure

import com.langlez.core.cache.CacheProvider
import com.langlez.core.cache.get
import com.langlez.core.cache.getMany
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.QMember.Companion.member as QMember
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * JpaRepository를 안 쓰므로(QueryDSL만) SimpleJpaRepository가 공짜로 주던
 * 메서드별 트랜잭션이 없다. 쓰기 메서드에 직접 걸어준다 — 안 걸면 persist/merge/delete가
 * 트랜잭션 없이 호출돼 터진다. 서비스 레이어는 이 덕분에 단건 저장에 굳이
 * @Transactional을 안 걸어도 되고, 그만큼 서비스 메서드 안에서 트랜잭션이
 * 불필요하게 길어지는 걸(예: 저장 앞뒤로 네트워크 호출) 피할 수 있다.
 */
@Repository
@Transactional(readOnly = true)
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
    @Transactional
    override fun save(member: Member): Member =
        (if (member.id == 0L) member.also(em::persist) else em.merge(member)).also(::updateCaches)

    override fun find(id: Long): Member? = members.get<Member>(id)
        ?: dsl.selectFrom(QMember).where(QMember.id.eq(id)).fetchOne()?.also(::updateCaches)

    override fun find(handle: String): Member? = handles.get<String>(handle)?.toLongOrNull()
        ?.let(::find)?.takeIf { it.handle == handle }
        ?: dsl.selectFrom(QMember).where(QMember.handle.eq(handle)).fetchOne()?.also(::updateCaches)

    override fun find(provider: Member.Provider, id: String): Member? = providers.get<String>("$provider:$id")
        ?.toLongOrNull()
        ?.let(::find)?.takeIf { it.provider == provider && it.providerId == id }
        ?: dsl.selectFrom(QMember)
            .where(QMember.provider.eq(provider), QMember.providerId.eq(id))
            .fetchOne()?.also(::updateCaches)

    override fun findByEmail(email: String): Member? = emails.get<String>(email)?.toLongOrNull()
        ?.let(::find)?.takeIf { it.email == email }
        ?: dsl.selectFrom(QMember).where(QMember.email.eq(email)).fetchOne()?.also(::updateCaches)

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

    override fun count(): Long = dsl.select(QMember.count()).from(QMember).fetchOne() ?: 0L

    /**
     * delete(id) -> delete(member) 처럼 같은 클래스 안에서 this로 호출하는 건
     * 프록시를 안 거쳐서 @Transactional이 안 먹는다(self-invocation). 그래서
     * 델리게이션하는 쪽도 전부 각자 @Transactional을 직접 달아야 한다.
     */
    @Transactional
    override fun delete(id: Long) {
        find(id)?.let(::delete)
    }

    /**
     * 캐시 무효화에 필요한 email/handle/provider 키는 멤버를 읽어야 알 수 있다.
     * DB 를 먼저 지우면 그 조회가 실패해 캐시에 고아 키가 영구히 남는다. 반드시 먼저 읽는다.
     */
    @Transactional
    override fun delete(ids: List<Long>) = delete(findAll(ids))

    @Transactional
    override fun delete(member: Member) {
        dsl.delete(QMember).where(QMember.id.eq(member.id)).execute()
        evictCaches(member)
    }

    @Transactional
    override fun delete(members: Collection<Member>) {
        if (members.isEmpty()) return

        dsl.delete(QMember).where(QMember.id.`in`(members.map { it.id })).execute()
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
