package com.langlez.member.infrastructure.outbox

import com.langlez.member.infrastructure.jpa.MemberOutBoxRepository
import com.langlez.rdb.outbox.OutBoxProcessor
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
internal class MemberOutBoxScheduler(repo: MemberOutBoxRepository) : OutBoxProcessor<MemberOutBox>(repo) {

    override val chunk = 1000
    override val tries = 3
    override val threads = 10

    @Scheduled(cron = "*/2 * * * * *")
    @DistributedLock(prefix = "lock:member-outbox")
    override fun send() = super.send()
}
