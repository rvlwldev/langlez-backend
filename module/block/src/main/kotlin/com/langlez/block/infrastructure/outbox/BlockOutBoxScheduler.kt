package com.langlez.block.infrastructure.outbox

import com.langlez.block.infrastructure.jpa.BlockOutBoxRepository
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
 * 주기를 팔로우 아웃박스보다 늦추지 않는다. 여기서 늦추면 차단 후 팔로우 행이 남는 창이
 * 그만큼 길어진다 — 사용자에게 보이지는 않지만(`BlockService.block` KDoc) 짧을수록 좋다.
 */
@Component
internal class BlockOutBoxScheduler(repo: BlockOutBoxRepository) : OutBoxProcessor<BlockOutBox>(repo) {

    override val chunk = 1000
    override val tries = 3
    override val threads = 10

    @Scheduled(cron = "*/2 * * * * *")
    @DistributedLock(prefix = "lock:block-outbox")
    override fun send() = super.send()
}
