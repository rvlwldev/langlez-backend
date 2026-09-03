package com.langlez.follow.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.follow.contract.MemberFollowedEvent
import com.langlez.follow.infrastructure.jpa.FollowOutBoxRepository
import com.langlez.follow.infrastructure.outbox.FollowOutBox
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 팔로우 이벤트를 아웃박스 행으로 남긴다.
 *
 * BEFORE_COMMIT 이라 팔로우 저장과 아웃박스 기록이 한 트랜잭션에 묶인다.
 * 커밋 뒤에 남기면 그 사이 장애로 이벤트만 통째로 사라진다.
 * 키를 팔로우 대상 id 로 두면 같은 사람에 대한 이벤트가 같은 파티션에 들어가 순서가 보장된다.
 */
@Component
class FollowEventListener(
    private val repo: FollowOutBoxRepository,
    private val mapper: ObjectMapper,
) {

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun onMemberFollowed(event: MemberFollowedEvent) {
        repo.save(
            FollowOutBox(
                "FOLLOW",
                "member-followed",
                mapper.writeValueAsString(event),
                event.followedId.toString(),
            )
        )
    }
}
