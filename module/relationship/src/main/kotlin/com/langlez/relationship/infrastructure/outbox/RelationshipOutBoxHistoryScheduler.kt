package com.langlez.relationship.infrastructure.outbox

import com.langlez.rdb.outbox.OutBoxHistoryProcessor
import com.langlez.redis.distributedLock.DistributedLock
import com.langlez.relationship.infrastructure.jpa.RelationshipOutBoxRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 처리 끝난 아웃박스 행을 이력 테이블로 옮긴다.
 *
 * `relationship_event_outbox_history` 엔티티만 있고 채우는 쪽이 없어서
 * 발행이 끝난 행이 원본 테이블에 계속 쌓이고 있었다.
 */
@Component
internal class RelationshipOutBoxHistoryScheduler(repo: RelationshipOutBoxRepository) :
    OutBoxHistoryProcessor<RelationshipOutBox, RelationshipOutBoxHistory>(repo) {

    override val chunk = 1000

    override fun toHistory(outbox: RelationshipOutBox) = RelationshipOutBoxHistory(outbox)

    @Scheduled(cron = "0 0 6 * * *") // 매일 아침 6시
    @DistributedLock(prefix = "lock:relationship-outbox-history")
    override fun archive() = super.archive()
}
