package com.langlez.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.OutBoxEventPublisher
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CompletableFuture

@Service
internal class OutBoxService(
    private val repo: OutBoxRepository,
    private val kafka: KafkaTemplate<String, String>,
    private val mapper: ObjectMapper,
    private val transaction: TransactionTemplate
) : OutBoxEventPublisher {

    override fun publish(type: String, id: String, name: String, payload: Any?) {
        repo.save(OutBox(type, id, name, mapper.writeValueAsString(payload)))
    }

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(ttl = 3, wait = 0, retries = 0, throwOnFailure = false)
    internal fun dispatchEvents() {
        val events = repo.findAllTargetToDispatch()
        if (events.isEmpty()) return

        val futures = events.map { event ->
            event.dispatch()

            val topic = "topic-${event.aggregateType.lowercase()}"
            kafka.send(topic, event.aggregateId, event.payload)
                .toCompletableFuture()
                .handle { _, e ->
                    if (e == null) event.complete()
                    else event.fail()
                    event
                }
        }

        CompletableFuture.allOf(*futures.toTypedArray()).join()
        transaction.execute { repo.saveAll(events) }
    }

    @Scheduled(cron = "0 0 0 * * *")
    @DistributedLock
    fun archiveDispatchedEvents() {
        transaction.execute {
            repo.findAllCompleted().also { repo.deleteAll(it) }
                .map { OutBoxHistory(it) }.also { repo.saveAllHistory(it) }
        }
    }

}

