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

/**
 * `member` 캐시는 값 객체가 아니라 [Member] 엔티티를 그대로 담는다 — 저장소 규약(`CLAUDE.md`
 * 부록의 "LAZY 연관을 가진 엔티티는 캐시하지 않는다")을 어긴 채로 남겨 둔 것이다.
 *
 * `find(handle)`/`find(provider, id)`/`find(id)` 는 전부 `audit` 을 함께 fetch 한 뒤에만
 * 캐시에 넣으므로(join fetch / `@EntityGraph`), 초기화 안 된 프록시가 직렬화되는 사고는 없다.
 * 남는 위험은 두 갈래다.
 * 1. **폴백 캐시가 같은 힙 인스턴스를 공유하는 것** — Redis 는 JSON 코덱이라 매 조회가 새 인스턴스지만,
 *    Redis 장애 시 붙는 로컬 Caffeine 은 그러지 않았다. `CaffeineCache` 를 직렬화 왕복으로 바꿔 막았다.
 * 2. **캐시 TTL(10분) 동안 `@Version` 이 낡을 수 있는 것** — 그 값을 들고 `repo.save()` 를 부르면
 *    `merge()` 가 `ObjectOptimisticLockingFailureException` 을 던진다. 이건 고치지 않았다. 대신
 *    [com.langlez.member.application.MemberService.updateHandle] 에서 그 예외를
 *    핸들 중복(409)으로 오인하던 버그만 없앴다 — 실제 원인 그대로 드러난다.
 *
 * 값 객체로 갈아타면 2번까지 없앨 수 있지만 [find]·[save] 를 쓰는 모든 유스케이스가 캐시 스냅샷과
 * 관리 대상 엔티티를 오가야 해서 파급이 크다. 지금은 1번(진짜 힙 손상)만 막고 2번은 README
 * "알려진 결함" 목록에 남긴다.
 */
@Repository
class MemberRepositoryImpl(
    private val jpa: MemberJpaRepository,
    private val dsl: JPAQueryFactory,
    caches: CacheProvider,
) : MemberRepository {

    private val members = caches.getCache("member")

    override fun save(member: Member): Member = jpa.save(member).also(::updateCaches)

    /**
     * 캐시 히트면 캐시에 다시 쓰지 않는다.
     *
     * `JwtAuthenticationFilter` 가 매 요청 상태 검사로 이 경로를 타는데, 히트마다 되쓰면
     * 요청당 Redis SET 이 붙는 것에 더해 TTL 이 계속 갱신돼 한 번 박힌 낡은 값이
     * **영영 만료되지 않는다.** 정지된 회원이 캐시의 ACTIVE 로 계속 통과했던 원인이다.
     */
    override fun find(id: Long): Member? =
        members.get<Member>(id) ?: jpa.findWithAuditById(id)?.also(::cacheIfAbsent)

    override fun find(handle: String): Member? =
        dsl.selectFrom(QMember).leftJoin(QMember.audit).fetchJoin()
            .where(QMember.handle.eq(handle)).fetchOne()
            ?.also(::cacheIfAbsent)

    override fun find(provider: Member.Provider, id: String): Member? =
        dsl.selectFrom(QMember).leftJoin(QMember.audit).fetchJoin()
            .where(QMember.provider.eq(provider), QMember.providerId.eq(id)).fetchOne()
            ?.also(::cacheIfAbsent)

    override fun findByEmail(email: String): Member? =
        dsl.selectFrom(QMember).leftJoin(QMember.audit).fetchJoin()
            .where(QMember.email.eq(email)).fetchOne()
            ?.also(::cacheIfAbsent)

    override fun findAll(ids: Collection<Long>): List<Member> {
        if (ids.isEmpty()) return emptyList()
        val distinct = ids.toSet()

        val cached = members.getMany<Member>(distinct)
        val missing = distinct.filter { it !in cached }
        if (missing.isEmpty()) return cached.values.toList()

        val loaded = dsl.selectFrom(QMember).leftJoin(QMember.audit).fetchJoin().where(QMember.id.`in`(missing)).fetch()
        members.putManyIfAbsent(loaded.associateBy { it.id })

        return cached.values + loaded
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

    /** 쓰기 경로 전용. 저장한 값이 최신이므로 무조건 덮어쓴다. */
    private fun updateCaches(member: Member) = members.put(member.id, member)

    /**
     * 읽기 경로(read-through) 전용. 비어 있을 때만 채운다.
     *
     * 커밋 전 DB 를 읽은 요청이 커밋 후 갱신보다 늦게 캐시에 도착해 최종 상태를 덮어쓰는 창을
     * 닫는다. 순서가 어떻든 마지막 승자는 쓰기 경로다 — 읽기가 먼저 채웠으면 [updateCaches] 가
     * 덮고, 쓰기가 먼저 채웠으면 읽기가 못 덮는다.
     */
    private fun cacheIfAbsent(member: Member) = members.putIfAbsent(member.id, member)

    private fun evictCaches(member: Member) = members.evict(member.id)

}
