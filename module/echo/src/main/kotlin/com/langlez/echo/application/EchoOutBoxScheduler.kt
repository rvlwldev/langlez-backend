package com.langlez.echo.application

import com.langlez.core.MessageQueue
import com.langlez.echo.infrastructure.outbox.EchoOutBox
import com.langlez.echo.infrastructure.outbox.EchoOutBoxHistory
import com.langlez.echo.infrastructure.outbox.EchoOutBoxRepository
import com.langlez.mysql.outbox.AbstractOutBoxScheduler
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class EchoOutBoxScheduler(
    repo: EchoOutBoxRepository,
    messageQueue: MessageQueue,
    transaction: TransactionTemplate,
) : AbstractOutBoxScheduler<EchoOutBox, EchoOutBoxHistory>(repo, messageQueue, transaction) {

    override fun toHistory(outbox: EchoOutBox) = EchoOutBoxHistory(outbox)

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:echo-outbox", ttl = 30, wait = 0, retries = 0, throwOnFailure = false)
    override fun dispatchEvents() = super.dispatchEvents()

    @Scheduled(cron = "0 0 0 * * *")
    @DistributedLock(prefix = "lock:echo-outbox-archive")
    override fun archiveEvents() = super.archiveEvents()
}
