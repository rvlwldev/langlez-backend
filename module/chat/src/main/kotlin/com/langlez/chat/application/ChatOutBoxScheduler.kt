package com.langlez.chat.application

import com.langlez.chat.infrastructure.outbox.ChatOutBoxHistory
import com.langlez.chat.infrastructure.outbox.ChatOutBoxRepository
import com.langlez.core.MessageQueue
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class ChatOutBoxScheduler(
    private val repo: ChatOutBoxRepository,
    private val messageQueue: MessageQueue,
    private val transaction: TransactionTemplate,
) {
    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:chat-outbox", ttl = 3, wait = 0, retries = 0, throwOnFailure = false)
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
    @DistributedLock(prefix = "lock:chat-outbox-archive")
    fun archiveEvents() {
        transaction.execute {
            val completedEvents = repo.findAllCompleted()
            if (completedEvents.isNotEmpty()) {
                repo.deleteAll(completedEvents)
                val historyList = completedEvents.map { ChatOutBoxHistory(it) }
                repo.saveAllHistory(historyList)
            }
        }
    }
}
