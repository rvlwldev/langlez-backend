package com.langlez.member.infrastructure.outbox

import com.langlez.member.infrastructure.jpa.MemberOutBoxRepository
import com.langlez.rdb.outbox.OutBoxHistoryProcessor
import com.langlez.rdb.outbox.OutBoxProcessor
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
internal class MemberOutBoxHistoryScheduler(repo: MemberOutBoxRepository) :
    OutBoxHistoryProcessor<MemberOutBox, MemberOutBoxHistory>(repo) {

    override val chunk = 1000

    override fun toHistory(outbox: MemberOutBox) = MemberOutBoxHistory(outbox)

    @Scheduled(cron = "0 0 6 * * *") // 매일 아침 6시
    @DistributedLock(prefix = "lock:member-outbox-history")
    override fun archive() = super.archive()
}
