package com.langlez.chat.application

import com.langlez.core.MessageQueue
import com.langlez.chat.infrastructure.outbox.ChatOutBox
import com.langlez.chat.infrastructure.outbox.ChatOutBoxHistory
import com.langlez.chat.infrastructure.outbox.ChatOutBoxRepository
import com.langlez.mysql.outbox.AbstractOutBoxScheduler
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class ChatOutBoxScheduler(
    repo: ChatOutBoxRepository,
    messageQueue: MessageQueue,
    transaction: TransactionTemplate,
) : AbstractOutBoxScheduler<ChatOutBox, ChatOutBoxHistory>(repo, messageQueue, transaction) {

    override fun toHistory(outbox: ChatOutBox) = ChatOutBoxHistory(outbox)

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:chat-outbox", ttl = 30, wait = 0, retries = 0, throwOnFailure = false)
    override fun dispatchEvents() = super.dispatchEvents()

    @Scheduled(cron = "0 0 0 * * *")
    @DistributedLock(prefix = "lock:chat-outbox-archive")
    override fun archiveEvents() = super.archiveEvents()
}
