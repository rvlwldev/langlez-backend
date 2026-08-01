package com.langlez.member.infrastructure

import com.langlez.mysql.outbox.OutBoxProcessor
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
internal class MemberOutBoxScheduler(
    repo: MemberOutBoxRepositoryImpl,
    kafka: KafkaTemplate<String, String>,
    tx: TransactionTemplate,
) : OutBoxProcessor<MemberOutBox, MemberOutBoxHistory>(
    repo = repo,
    kafka = kafka,
    tx = tx,
    toHistory = ::MemberOutBoxHistory,
) {

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:member-outbox")
    override fun dispatchEvents() = super.dispatchEvents()

    @Scheduled(cron = "0 0 2 * * *")
    @DistributedLock(prefix = "lock:member-outbox-history")
    override fun moveToHistory() = super.moveToHistory()
}
