package com.langlez.echo.application

import com.langlez.core.MessageQueue
import com.langlez.echo.infrastructure.outbox.EchoOutBoxHistory
import com.langlez.echo.infrastructure.outbox.EchoOutBoxRepository
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class EchoOutBoxScheduler(
    private val repo: EchoOutBoxRepository,
    private val messageQueue: MessageQueue,
    private val transaction: TransactionTemplate,
) {
    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:echo-outbox", ttl = 30, wait = 0, retries = 0, throwOnFailure = false)
    fun dispatchEvents() {
        val events = repo.findToDispatch(100)
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
    @DistributedLock(prefix = "lock:echo-outbox-archive")
    fun archiveEvents() {
        var processed: List<com.langlez.echo.infrastructure.outbox.EchoOutBox>
        do {
            processed = transaction.execute {
                val events = repo.findCompletedOrFailed(100)
                if (events.isNotEmpty()) {
                    repo.deleteAll(events)
                    repo.saveAllHistory(events.map { EchoOutBoxHistory(it) })
                }
                events
            } ?: emptyList()
        } while (processed.size == 100)
    }
}
