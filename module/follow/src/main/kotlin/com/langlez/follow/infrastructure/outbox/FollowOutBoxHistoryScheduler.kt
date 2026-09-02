package com.langlez.follow.infrastructure.outbox

import com.langlez.follow.infrastructure.jpa.FollowOutBoxRepository
import com.langlez.rdb.outbox.OutBoxHistoryProcessor
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 처리 끝난 아웃박스 행을 이력 테이블로 옮긴다.
 *
 * 이 스케줄러가 없으면 이력 엔티티만 있고 채우는 쪽이 없어서
 * 발행이 끝난 행이 원본 테이블에 계속 쌓인다.
 */
@Component
internal class FollowOutBoxHistoryScheduler(repo: FollowOutBoxRepository) :
    OutBoxHistoryProcessor<FollowOutBox, FollowOutBoxHistory>(repo) {

    override val chunk = 1000

    override fun toHistory(outbox: FollowOutBox) = FollowOutBoxHistory(outbox)

    @Scheduled(cron = "0 0 6 * * *") // 매일 아침 6시
    @DistributedLock(prefix = "lock:follow-outbox-history")
    override fun archive() = super.archive()
}
