package com.langlez.relationship.application

import com.langlez.core.MessageQueue
import com.langlez.mysql.outbox.AbstractOutBoxScheduler
import com.langlez.redis.distributedLock.DistributedLock
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBox
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBoxHistory
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBoxRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class RelationshipOutBoxScheduler(
    repo: RelationshipOutBoxRepository,
    mq: MessageQueue,
    tx: TransactionTemplate,
) : AbstractOutBoxScheduler<RelationshipOutBox, RelationshipOutBoxHistory>(
    repo = repo,
    mq = mq,
    tx = tx,
    toHistory = ::RelationshipOutBoxHistory,
) {

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:relationship-outbox", ttl = -1, wait = 0, retries = 0, throwOnFailure = false)
    override fun dispatchEvents() = super.dispatchEvents()

    @Scheduled(cron = "0 0 2 * * *")
    @DistributedLock(prefix = "lock:relationship-outbox-history", ttl = -1, wait = 0, retries = 0, throwOnFailure = false)
    override fun moveToHistory() = super.moveToHistory()
}
