package com.langlez.member.infrastructure

import com.langlez.member.domain.MemberSuspendHistory
import com.langlez.member.domain.MemberSuspendHistoryRepository
import com.langlez.member.infrastructure.jpa.MemberSuspendHistoryJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.Instant
import com.langlez.member.domain.QMemberSuspendHistory.Companion.memberSuspendHistory as QHistory

/** 캐시를 두지 않는다. 운영 조치 이력이라 쓰기가 드물고 읽기는 배치와 해제 경로뿐이다. */
@Repository
class MemberSuspendHistoryRepositoryImpl(
    private val jpa: MemberSuspendHistoryJpaRepository,
    private val dsl: JPAQueryFactory,
) : MemberSuspendHistoryRepository {

    override fun save(history: MemberSuspendHistory): MemberSuspendHistory = jpa.save(history)

    override fun saveAll(histories: Collection<MemberSuspendHistory>): List<MemberSuspendHistory> =
        if (histories.isEmpty()) emptyList() else jpa.saveAll(histories)

    override fun findOpen(memberId: Long): List<MemberSuspendHistory> = dsl.selectFrom(QHistory)
        .where(QHistory.memberId.eq(memberId), QHistory.isReleased.isFalse)
        .fetch()

    /**
     * `IDX_MEMBER_SUSPEND_RELEASED(member_id, is_released)` 는 선두 컬럼이 member_id 라
     * 이 조회를 못 탄다. 만료 대상은 전체에서 봐도 몇 건 수준이라 인덱스를 새로 걸지 않았다.
     * 정지가 늘어 이 스캔이 무거워지면 `(is_released, release_at)` 인덱스를 붙인다.
     */
    override fun findExpired(now: Instant, size: Int): List<MemberSuspendHistory> = dsl.selectFrom(QHistory)
        .where(
            QHistory.isReleased.isFalse,
            // 무기한 정지는 releaseAt 이 null 이다. loe(null) 이 아니라 조건 자체가 걸러낸다.
            QHistory.releaseAt.isNotNull,
            QHistory.releaseAt.loe(now),
        )
        .orderBy(QHistory.id.asc())
        .limit(size.toLong())
        .fetch()
}
