package com.langlez.relationship.infrastructure

import com.langlez.core.BlockQuery
import com.langlez.relationship.infrastructure.jpa.BlockJpaRepository
import org.springframework.stereotype.Repository

@Repository
class BlockQueryImpl(
    private val jpa: BlockJpaRepository,
) : BlockQuery {

    /**
     * 차단 행은 단방향(blocker → blocked)으로만 저장된다.
     * 채팅·매칭은 누가 먼저 차단했든 상호작용을 막아야 하므로 양방향을 모두 본다.
     */
    override fun isBlockedBetween(memberId: Long, otherId: Long): Boolean =
        jpa.existsByBlockerIdAndBlockedId(memberId, otherId) ||
            jpa.existsByBlockerIdAndBlockedId(otherId, memberId)
}
