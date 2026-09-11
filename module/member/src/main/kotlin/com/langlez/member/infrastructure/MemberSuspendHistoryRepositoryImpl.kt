package com.langlez.member.infrastructure

import com.langlez.member.domain.MemberSuspendHistory
import com.langlez.member.domain.MemberSuspendHistoryRepository
import com.langlez.member.infrastructure.jpa.MemberSuspendHistoryJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
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
     * 이 조회를 못 탔다. 닫힌 이력이 누적되어도 풀스캔을 방지하도록
     * V24 에서 `where is_released = false` 부분 인덱스 `IDX_MEMBER_SUSPEND_EXPIRED(release_at, id)` 를 추가했다.
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

    @Transactional
    override fun releaseActive(memberId: Long) {
        dsl.update(QHistory)
            .set(QHistory.isReleased, true)
            .where(QHistory.memberId.eq(memberId), QHistory.isReleased.isFalse)
            .execute()
    }
}
