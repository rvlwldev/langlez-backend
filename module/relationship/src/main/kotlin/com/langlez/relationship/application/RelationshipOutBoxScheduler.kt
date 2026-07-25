package com.langlez.relationship.application

import com.langlez.core.MessageQueue
import com.langlez.redis.distributedLock.DistributedLock
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBox
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBoxHistory
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBoxRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class RelationshipOutBoxScheduler(
    private val repo: RelationshipOutBoxRepository,
    private val messageQueue: MessageQueue,
    private val transaction: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(RelationshipOutBoxScheduler::class.java)

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:relationship-outbox", ttl = 30, wait = 0, retries = 0, throwOnFailure = false)
    fun dispatchEvents() {
        val events = repo.findToDispatch(100)
        if (events.isEmpty()) return

        events.forEach { event ->
            event.dispatch()
            runCatching {
                messageQueue.publish("topic-${event.aggregateType.lowercase()}", event.aggregateId, event.payload)
            }.onSuccess {
                event.complete()
            }.onFailure {
                event.fail()
                if (event.status == RelationshipOutBox.Status.FAILED) {
                    log.error(
                        "Outbox event failed permanently. id={}, aggregateType={}, aggregateId={}, eventName={}",
                        event.id, event.aggregateType, event.aggregateId, event.eventName
                    )
                }
            }
            transaction.execute { repo.save(event) }
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    @DistributedLock(prefix = "lock:relationship-outbox-archive")
    fun archiveEvents() {
        val chunkSize = 100
        while (true) {
            val completedList = repo.findCompleted(chunkSize)
            if (completedList.isEmpty()) break

            transaction.execute {
                repo.deleteAll(completedList)
                val history = completedList.map { RelationshipOutBoxHistory(it) }
                repo.saveAllHistory(history)
            }

            if (completedList.size < chunkSize) break
        }
    }
}
