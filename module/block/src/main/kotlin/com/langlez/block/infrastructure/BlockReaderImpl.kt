package com.langlez.block.infrastructure

import com.langlez.block.contract.BlockReader
import com.langlez.block.infrastructure.jpa.BlockJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import com.langlez.block.domain.QBlock.Companion.block as QBlock

/**
 * block 이 다른 모듈에 내주는 조회 포트 구현.
 *
 * chat·echo·follow 가 차단을 보는 유일한 통로다. 구현이 없으면 그 경로들이 통째로 죽으므로
 * block 모듈이 반드시 이 빈을 올려야 한다.
 */
@Repository
class BlockReaderImpl(
    private val jpa: BlockJpaRepository,
    private val dsl: JPAQueryFactory,
) : BlockReader {

    /**
     * 차단 행은 단방향(blocker → blocked)으로만 저장된다.
     * 채팅·매칭은 누가 먼저 차단했든 상호작용을 막아야 하므로 양방향을 모두 본다.
     */
    override fun isBlockedBetween(memberId: Long, otherId: Long): Boolean =
        jpa.existsByBlockerIdAndBlockedId(memberId, otherId) ||
            jpa.existsByBlockerIdAndBlockedId(otherId, memberId)

    /**
     * 한 방에 판정한다. 목록 항목마다 [isBlockedBetween] 을 돌면 이 포트가 원격이 될 때
     * 항목 수만큼 왕복이 생긴다 — 그게 이 포트를 배치로 넓힌 이유다.
     *
     * 판정 규칙은 [isBlockedBetween] 과 같다(양방향). 조회 조건이 이미 (viewer, 후보) 쌍으로
     * 좁혀져 있으므로, 나온 두 컬럼에서 후보 id 만 골라내면 그게 곧 차단된 상대다 —
     * CASE 로 방향을 되돌릴 필요가 없다.
     */
    override fun blockedAmong(viewerId: Long, candidateIds: Collection<Long>): Set<Long> {
        // 자기 자신은 차단 대상이 될 수 없다(Block 이 생성 시점에 막는다). 넣어 봐야 조건만 넓어진다.
        val ids = candidateIds.toSet() - viewerId
        if (ids.isEmpty()) return emptySet()

        return dsl.select(QBlock.blockerId, QBlock.blockedId)
            .from(QBlock)
            .where(
                QBlock.blockerId.eq(viewerId).and(QBlock.blockedId.`in`(ids))
                    .or(QBlock.blockedId.eq(viewerId).and(QBlock.blockerId.`in`(ids)))
            )
            .fetch()
            .flatMap { listOfNotNull(it.get(QBlock.blockerId), it.get(QBlock.blockedId)) }
            .filterTo(mutableSetOf()) { it in ids }
    }
}
