package com.langlez.echo.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.echo.infrastructure.outbox.EchoOutBoxArchive
import com.langlez.echo.infrastructure.outbox.EchoOutBoxArchiveRepository
import com.langlez.echo.infrastructure.outbox.EchoOutBoxHistory
import com.langlez.mysql.outbox.OutBoxArchiver
import com.langlez.redis.distributedLock.DistributedLock
import java.time.LocalDate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class EchoOutBoxArchiveScheduler(
    archiveRepo: EchoOutBoxArchiveRepository,
    tx: TransactionTemplate,
    objectMapper: ObjectMapper,
) : OutBoxArchiver<EchoOutBoxHistory, EchoOutBoxArchive>(
    repo = archiveRepo,
    tx = tx,
    mapper = objectMapper,
    toArchive = ::EchoOutBoxArchive,
) {

    @Scheduled(cron = "0 0 3 1 * *")
    @DistributedLock(prefix = "lock:echo-outbox-archive", leaseSecs = -1, waitMs = 0, retries = 0, throwOnFailure = false)
    override fun archive(before: LocalDate) = super.archive(before)
}
