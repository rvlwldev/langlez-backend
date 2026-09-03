package com.langlez.follow.infrastructure.outbox

import com.langlez.follow.infrastructure.jpa.FollowOutBoxRepository
import com.langlez.rdb.outbox.OutBoxProcessor
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 아웃박스 → 카프카 발행.
 *
 * `@DistributedLock` 은 필수다. fetch 의 SKIP LOCKED 는 조회 트랜잭션이 끝나는 순간 풀려서
 * 인스턴스가 여러 대면 같은 행을 두 번 발행할 수 있다. 중복을 실제로 막는 건 이 락이다.
 *
 * 주기·chunk·threads 는 member/chat 아웃박스와 같은 값이다. 팔로우 이벤트가 그것들보다
 * 드물다고 낮출 이유가 없다 — 낮추면 알림 지연만 늘고 절약되는 건 빈 조회 한 번뿐이다.
 */
@Component
internal class FollowOutBoxScheduler(repo: FollowOutBoxRepository) : OutBoxProcessor<FollowOutBox>(repo) {

    override val chunk = 1000
    override val tries = 3
    override val threads = 10

    @Scheduled(cron = "*/2 * * * * *")
    @DistributedLock(prefix = "lock:follow-outbox")
    override fun send() = super.send()
}
