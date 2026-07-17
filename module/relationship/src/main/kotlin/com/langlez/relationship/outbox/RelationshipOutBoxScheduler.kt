package com.langlez.relationship.outbox

import com.langlez.core.MessageQueue
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class RelationshipOutBoxScheduler(
    private val repo: RelationshipOutBoxRepository,
    private val messageQueue: MessageQueue,
    private val transaction: TransactionTemplate,
) {
    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:relationship-outbox", ttl = 3, wait = 0, retries = 0, throwOnFailure = false)
    fun dispatchEvents() {
        val events = repo.findToDispatch(500)
        if (events.isEmpty()) return

        events.forEach { event ->
            event.dispatch()
            runCatching {
                messageQueue.publish("topic-${event.aggregateType.lowercase()}", event.aggregateId, event.payload)
            }.onSuccess { event.complete() }.onFailure { event.fail() }
        }

        transaction.execute { repo.saveAll(events) }
    }

    @Scheduled(cron = "0 0 0 * * *")
    @DistributedLock(prefix = "lock:relationship-outbox-archive")
    fun archiveEvents() {
        transaction.execute {
            repo.findAllCompleted().also { repo.deleteAll(it) }
                .map { RelationshipOutBoxHistory(it) }.also { repo.saveAllHistory(it) }
        }
    }
}
