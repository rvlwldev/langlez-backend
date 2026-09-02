package com.langlez.block.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.block.contract.MemberBlockedEvent
import com.langlez.block.infrastructure.jpa.BlockOutBoxRepository
import com.langlez.block.infrastructure.outbox.BlockOutBox
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 차단 이벤트를 아웃박스 행으로 남긴다.
 *
 * BEFORE_COMMIT 이라 차단 저장과 아웃박스 기록이 한 트랜잭션에 묶인다.
 * 커밋 뒤에 남기면 그 사이 장애로 이벤트만 통째로 사라지고, 그러면 팔로우 행이 영영 안 끊긴다.
 *
 * 키를 **차단당한 쪽 id** 로 둔다. 같은 사람에 대한 이벤트가 같은 파티션에 들어가 순서가
 * 보장된다 — `FollowEventListener` 가 `followedId` 를 쓰는 것과 같은 이유다.
 */
@Component
class BlockEventListener(
    private val repo: BlockOutBoxRepository,
    private val mapper: ObjectMapper,
) {

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun onMemberBlocked(event: MemberBlockedEvent) {
        repo.save(
            BlockOutBox(
                "BLOCK",
                "member-blocked",
                mapper.writeValueAsString(event),
                event.blockedId.toString(),
            )
        )
    }
}
