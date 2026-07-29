package com.langlez.echo.application

import com.langlez.core.MessageProducer
import com.langlez.echo.infrastructure.outbox.EchoOutBox
import com.langlez.echo.infrastructure.outbox.EchoOutBoxHistory
import com.langlez.echo.infrastructure.outbox.EchoOutBoxRepository
import com.langlez.mysql.outbox.OutBoxProcessor
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class EchoOutBoxScheduler(
    repo: EchoOutBoxRepository,
    mq: MessageProducer,
    tx: TransactionTemplate,
) : OutBoxProcessor<EchoOutBox, EchoOutBoxHistory>(
    repo = repo,
    producer = mq,
    tx = tx,
    toHistory = ::EchoOutBoxHistory,
) {

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:echo-outbox", leaseSecs = -1, waitMs = 0, retries = 0, throwOnFailure = false)
    override fun dispatchEvents() = super.dispatchEvents()

    @Scheduled(cron = "0 0 2 * * *")
    @DistributedLock(prefix = "lock:echo-outbox-history", leaseSecs = -1, waitMs = 0, retries = 0, throwOnFailure = false)
    override fun moveToHistory() = super.moveToHistory()
}
