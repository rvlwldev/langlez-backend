package com.langlez.mysql.outbox

import com.langlez.core.MessageQueue
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate

/**
 * OutBox 발행/이관 루프의 공통 구현.
 *
 * 하위 클래스는 `@Component`로 등록하고, 두 메서드를 오버라이드해 `@Scheduled` +
 * `@DistributedLock`만 붙인다(락 prefix가 모듈마다 달라 어노테이션은 상속되지 않는다).
 *
 * ```
 * @Scheduled(fixedDelay = 5000)
 * @DistributedLock(prefix = "lock:echo-outbox", ttl = 30, wait = 0, retries = 0, throwOnFailure = false)
 * override fun dispatchEvents() = super.dispatchEvents()
 * ```
 *
 * 락 `ttl`은 반드시 한 배치를 처리하는 데 걸리는 시간보다 넉넉해야 한다.
 * 락이 먼저 만료되면 아직 상태가 DB에 반영되기 전이라 다른 인스턴스가 같은 이벤트를 재발행한다.
 */
abstract class AbstractOutBoxScheduler<T : AbstractOutBox, H : AbstractOutBoxHistory>(
    protected val repo: OutBoxRepository<T, H>,
    protected val messageQueue: MessageQueue,
    protected val transaction: TransactionTemplate,
) {

    protected val log = LoggerFactory.getLogger(javaClass)

    /** 이관 시 원본 엔티티를 모듈별 히스토리 엔티티로 변환한다. */
    protected abstract fun toHistory(outbox: T): H

    /**
     * 발행 결과는 건별로 커밋한다. 배치 끝에 한 번에 저장하면 루프 도중 프로세스가 죽었을 때
     * 이미 발행된 이벤트의 상태와 attempts 증가분이 통째로 유실되어 재발행 + 재시도 카운트 리셋이 된다.
     */
    open fun dispatchEvents() {
        val events = repo.findToDispatch(DISPATCH_BATCH_SIZE)
        if (events.isEmpty()) return

        events.forEach { event ->
            try {
                event.dispatch()
            } catch (e: IllegalStateException) {
                // 잘못된 상태 전이(poison row) — 건너뛰기만 하면 createdAt ASC 정렬 때문에 매 사이클
                // 똑같은 row가 다시 걸려 영원히 재발생한다. fail()로 강제 전이시켜 격리한다
                // (attempts가 이미 소진된 상태라 fail()은 FAILED로 확정 전이시킨다).
                log.error("Outbox event in unexpected state, marking FAILED. id={}, status={}, attempts={}", event.id, event.status, event.attempts, e)
                event.fail()
                transaction.execute { repo.saveAll(listOf(event)) }
                return@forEach
            }
            runCatching {
                messageQueue.publish("topic-${event.aggregateType.lowercase()}", event.aggregateId, event.payload)
            }.onSuccess {
                event.complete()
            }.onFailure {
                event.fail()
                if (event.status == OutBoxStatus.FAILED) {
                    log.error(
                        "Outbox event failed permanently. id={}, aggregateType={}, aggregateId={}, eventName={}",
                        event.id, event.aggregateType, event.aggregateId, event.eventName,
                    )
                }
            }
            transaction.execute { repo.saveAll(listOf(event)) }
        }
    }

    /**
     * 완료(COMPLETE)/실패(FAILED) 이벤트를 히스토리로 옮긴다.
     * 전량을 한 번에 읽으면 누적된 만큼 메모리를 먹으므로 페이지 단위로 반복 처리한다.
     * FAILED도 함께 이관해야 재발행 대상도 이관 대상도 아닌 데드 로우로 남지 않는다.
     */
    open fun archiveEvents() {
        var processed: List<T>
        do {
            processed = transaction.execute {
                val events = repo.findCompletedOrFailed(ARCHIVE_BATCH_SIZE)
                if (events.isNotEmpty()) {
                    repo.deleteAll(events)
                    repo.saveAllHistory(events.map { toHistory(it) })
                }
                events
            } ?: emptyList()
        } while (processed.size == ARCHIVE_BATCH_SIZE)
    }

    companion object {
        const val DISPATCH_BATCH_SIZE = 100
        const val ARCHIVE_BATCH_SIZE = 100
    }
}
