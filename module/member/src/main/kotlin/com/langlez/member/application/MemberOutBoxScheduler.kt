package com.langlez.member.application

import com.langlez.core.MessageQueue
import com.langlez.member.infrastructure.outbox.MemberOutBox
import com.langlez.member.infrastructure.outbox.MemberOutBoxHistory
import com.langlez.member.infrastructure.outbox.MemberOutBoxRepository
import com.langlez.mysql.outbox.AbstractOutBoxScheduler
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class MemberOutBoxScheduler(
    repo: MemberOutBoxRepository,
    messageQueue: MessageQueue,
    transaction: TransactionTemplate,
) : AbstractOutBoxScheduler<MemberOutBox, MemberOutBoxHistory>(repo, messageQueue, transaction) {

    override fun toHistory(outbox: MemberOutBox) = MemberOutBoxHistory(outbox)

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:member-outbox", ttl = 30, wait = 0, retries = 0, throwOnFailure = false)
    override fun dispatchEvents() = super.dispatchEvents()

    @Scheduled(cron = "0 0 0 * * *")
    @DistributedLock(prefix = "lock:member-outbox-archive")
    override fun archiveEvents() = super.archiveEvents()
}
