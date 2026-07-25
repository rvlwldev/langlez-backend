package com.langlez.relationship.application

import com.langlez.core.MessageQueue
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBox
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBoxHistory
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBoxRepository
import com.langlez.mysql.outbox.AbstractOutBoxScheduler
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class RelationshipOutBoxScheduler(
    repo: RelationshipOutBoxRepository,
    messageQueue: MessageQueue,
    transaction: TransactionTemplate,
) : AbstractOutBoxScheduler<RelationshipOutBox, RelationshipOutBoxHistory>(repo, messageQueue, transaction) {

    override fun toHistory(outbox: RelationshipOutBox) = RelationshipOutBoxHistory(outbox)

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:relationship-outbox", ttl = 30, wait = 0, retries = 0, throwOnFailure = false)
    override fun dispatchEvents() = super.dispatchEvents()

    @Scheduled(cron = "0 0 0 * * *")
    @DistributedLock(prefix = "lock:relationship-outbox-archive")
    override fun archiveEvents() = super.archiveEvents()
}
