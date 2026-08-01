package com.langlez.chat.application

import com.langlez.chat.infrastructure.outbox.ChatOutBox
import com.langlez.chat.infrastructure.outbox.ChatOutBoxHistory
import com.langlez.chat.infrastructure.outbox.ChatOutBoxRepository
import com.langlez.core.message.MessageProducer
import com.langlez.mysql.outbox.OutBoxProcessor
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class ChatOutBoxScheduler(
    repo: ChatOutBoxRepository,
    mq: MessageProducer,
    tx: TransactionTemplate,
) : OutBoxProcessor<ChatOutBox, ChatOutBoxHistory>(
    repo = repo,
    producer = mq,
    tx = tx,
    toHistory = ::ChatOutBoxHistory,
) {

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:chat-outbox", leaseSecs = -1, waitMs = 0, retries = 0, throwOnFailure = false)
    override fun dispatchEvents() = super.dispatchEvents()

    @Scheduled(cron = "0 0 2 * * *")
    @DistributedLock(prefix = "lock:chat-outbox-history", leaseSecs = -1, waitMs = 0, retries = 0, throwOnFailure = false)
    override fun moveToHistory() = super.moveToHistory()
}
