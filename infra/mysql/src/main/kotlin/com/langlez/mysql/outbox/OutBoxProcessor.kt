package com.langlez.mysql.outbox

import com.langlez.core.MessageProducer
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.transaction.support.TransactionTemplate

/**
 * OutBox 발행/이관 루프의 공통 구현.
 *
 * 하위 클래스는 `@Component`로 등록하고, 메서드를 오버라이드해 `@Scheduled` +
 * `@DistributedLock`만 붙인다(락 prefix가 모듈마다 달라 어노테이션은 상속되지 않는다).
 *
 * ```
 * @Scheduled(fixedDelay = 5000)
 * @DistributedLock(prefix = "lock:echo-outbox", leaseSecs = 0, waitMs = 0, retries = 0, throwOnFailure = false)
 * override fun dispatchEvents() = super.dispatchEvents()
 * ```
 *
 * [분산 락 설정 안내]
 * - `leaseSecs = 0` (기본값): Redisson Watchdog 자동 연장이 활성화되어, 작업이 진행 중인 동안 락 만료 시간이 10초마다 자동 연장됩니다.
 * - 작업 도중 서버 인스턴스가 다운되거나 프로세스가 죽으면 Watchdog이 정지되어 30초 후 레디스가 자동으로 락을 해제합니다.
 */
abstract class OutBoxProcessor<T : OutBox, H : OutBoxHistory>(
    protected val repo: OutBoxRepository<T, H>,
    private val producer: MessageProducer,
    private val tx: TransactionTemplate,
    private val toHistory: (T) -> H,
) : DisposableBean {
    private val semaphore = Semaphore(4)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    open fun dispatchEvents() {
        val events = repo.findToDispatch(DISPATCH_BATCH_SIZE)
        if (events.isEmpty()) return

        events.map { event ->
            executor.submit {
                semaphore.acquire()

                try {
                    runCatching { event.dispatch() }
                        .onFailure {
                            event.fail()
                            repo.save(event)
                            return@submit
                        }

                    runCatching { event.run { producer.produce(topic, payload, key) } }
                        .onSuccess { event.complete() }
                        .onFailure { event.fail() }

                    repo.save(event)
                } finally {
                    semaphore.release()
                }
            }
        }.forEach { runCatching { it.get(10, TimeUnit.SECONDS) } }
    }

    /**
     * 처리 완료(COMPLETE) 및 최종 실패(FAILED) 이벤트를 OutBox 테이블에서 삭제하고 History 테이블로 이관한다.
     * 전량 조회로 인한 메모리 폭주(OOM)를 방지하기 위해 배치 단위(HISTORY_BATCH_SIZE)로 루프 처리한다.
     */
    open fun moveToHistory() {
        var processedCount: Int

        do {
            processedCount = tx.execute {
                val outboxes = repo.findAllProcessed(HISTORY_BATCH_SIZE)

                if (outboxes.isNotEmpty()) {
                    repo.deleteAll(outboxes)
                    repo.saveAllHistory(outboxes.map { toHistory(it) })
                }

                outboxes.size
            } ?: 0
        } while (processedCount == HISTORY_BATCH_SIZE)
    }

    override fun destroy() {
        runCatching {
            executor.shutdown()
            if (!executor.awaitTermination(5, TimeUnit.SECONDS))
                executor.shutdownNow()
        }
    }

    companion object {
        const val DISPATCH_BATCH_SIZE = 100
        const val HISTORY_BATCH_SIZE = 100
    }
}