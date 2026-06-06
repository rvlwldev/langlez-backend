package com.langlez.member.outbox

import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CompletableFuture

@Component
internal class MemberOutBoxScheduler(
    private val repo: MemberOutBoxRepository,
    private val kafka: KafkaTemplate<String, Any>,
    private val transaction: TransactionTemplate,
) {
    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:member-outbox", ttl = 3, wait = 0, retries = 0, throwOnFailure = false)
    fun dispatchEvents() {
        val events = repo.findToDispatch(500)
        if (events.isEmpty()) return

        val futures = events.map { event ->
            event.dispatch()
            kafka.send("topic-${event.aggregateType.lowercase()}", event.aggregateId, event.payload)
                .toCompletableFuture()
                .handle { _, e ->
                    if (e == null) event.complete() else event.fail()
                    event
                }
        }

        CompletableFuture.allOf(*futures.toTypedArray()).join()
        transaction.execute { repo.saveAll(events) }
    }

    @Scheduled(cron = "0 0 0 * * *")
    @DistributedLock(prefix = "lock:member-outbox-archive")
    fun archiveEvents() {
        transaction.execute {
            repo.findAllCompleted().also { repo.deleteAll(it) }
                .map { MemberOutBoxHistory(it) }.also { repo.saveAllHistory(it) }
        }
    }
}
