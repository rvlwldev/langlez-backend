package com.langlez.follow.infrastructure.outbox

import com.langlez.follow.infrastructure.jpa.FollowOutBoxHistoryRepository
import com.langlez.rdb.outbox.OutBoxHistoryCleaner
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 보존 기간(90일)이 지난 이력 행을 지운다. 안 지우면 `follow_event_outbox_history` 가 무한 증가한다. */
@Component
internal class FollowOutBoxHistoryCleanupScheduler(repo: FollowOutBoxHistoryRepository) :
    OutBoxHistoryCleaner<FollowOutBoxHistory>(repo) {

    @Scheduled(cron = "0 30 6 * * *") // 매일 아침 6시 30분, 이관 스케줄러(6시) 다음
    @DistributedLock(prefix = "lock:follow-outbox-history-cleanup")
    override fun clean() = super.clean()
}
