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
    mq: MessageQueue,
    tx: TransactionTemplate,
) : AbstractOutBoxScheduler<MemberOutBox, MemberOutBoxHistory>(
    repo = repo,
    mq = mq,
    tx = tx,
    toHistory = ::MemberOutBoxHistory,
) {

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:member-outbox", ttl = -1, wait = 0, retries = 0, throwOnFailure = false)
    override fun dispatchEvents() = super.dispatchEvents()

    @Scheduled(cron = "0 0 2 * * *")
    @DistributedLock(prefix = "lock:member-outbox-history", ttl = -1, wait = 0, retries = 0, throwOnFailure = false)
    override fun moveToHistory() = super.moveToHistory()
}
