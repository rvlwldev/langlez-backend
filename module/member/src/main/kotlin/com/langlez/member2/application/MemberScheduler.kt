package com.langlez.member2.application

import com.langlez.member2.infrastructure.MemberRepository
import com.langlez.member2.infrastructure.jpa.MemberOutboxJpa
import com.langlez.rdb.outbox.OutBox
import com.langlez.rdb.outbox.OutBox.Status.PENDING
import com.langlez.redis.distributedLock.DistributedLock
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit.SECONDS

@Component
class MemberScheduler(
    private val repo: MemberRepository,
    private val outboxJpa: MemberOutboxJpa,
    private val kafka: KafkaTemplate<String, String>,
) {

    @Scheduled(cron = "*/1 * * * * *") // 1초 *분 *시 *일 *월 *요일
    @DistributedLock(prefix = "lock:member-outbox")
    fun dispatchOutboxes() {
        val retries = 3
        val timeout = 5L

        // TODO : MemberOutboxRepository 생성 후 QueryDSL로 chunk를 1000으로
        val outboxes = outboxJpa.findAllByStatus(PENDING)
        if (outboxes.isEmpty()) return

        val results = outboxes.map { outbox ->
            outbox.dispatch()
            outbox to kafka.send(ProducerRecord(outbox.topic, outbox.key, outbox.payload))
        }

        results.forEach { (outbox, future) ->
            try {
                future.get(timeout, SECONDS)
                outbox.complete()
            } catch (_: InterruptedException) {
                // 앱 종료 시
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                outbox.fail(retries)
            }
        }

        outboxJpa.saveAll(outboxes)
    }

    fun clearCompletedOutboxes() {
        val targets = outboxJpa.findAllByStatus(OutBox.Status.COMPLETE)
    }

    @Scheduled(cron = "0 0 0 * * *") // 0초 0분 0시 *일 *월 *요일
    @DistributedLock(prefix = "lock:member-suspend-release", transactional = true)
    fun releaseSuspension() {
        // 일시 정지 회원들 복귀
    }

}