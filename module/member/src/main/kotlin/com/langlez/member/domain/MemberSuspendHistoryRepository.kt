package com.langlez.member.domain

import java.time.Instant

interface MemberSuspendHistoryRepository {

    fun save(history: MemberSuspendHistory): MemberSuspendHistory

    fun saveAll(histories: Collection<MemberSuspendHistory>): List<MemberSuspendHistory>

    /** 아직 닫히지 않은 이력. 어드민이 정지를 풀 때 이 이력들도 함께 닫아야 만료 배치가 다시 잡지 않는다. */
    fun findOpen(memberId: Long): List<MemberSuspendHistory>

    /**
     * [now] 기준으로 만료됐는데 아직 닫히지 않은 이력.
     *
     * `releaseAt` 이 null 인 무기한 정지는 걸리지 않는다 — 기간 없는 정지는 사람이 풀어야 한다.
     */
    fun findExpired(now: Instant, size: Int): List<MemberSuspendHistory>
}
