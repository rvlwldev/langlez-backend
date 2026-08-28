package com.langlez.rdb.outbox

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS

abstract class OutBoxHistoryCleaner<H : OutBoxHistory>(private val repo: OutBoxHistoryRepository<H>) {

    @Autowired
    private lateinit var tx: TransactionTemplate

    open val chunk = 1000
    open val retentionDays = 90L

    /**
     * 스케줄러가 부르는 진입점. `@Scheduled` 는 인자 있는 메서드에 못 붙어서 무인자로 둔다.
     */
    open fun clean() = cleanBefore(Instant.now().minus(retentionDays, DAYS))

    /**
     * cutoff 를 직접 받는다. 테스트가 경계 시각을 고정할 수 있어야 하기 때문이다 —
     * [clean] 안에서 `Instant.now()` 를 부르면 테스트가 계산한 cutoff 와 여기서 계산한 cutoff 가
     * 몇 밀리초 어긋나, "딱 기준 시각인 행은 남는다"(strict `<`)를 검증할 방법이 없다.
     *
     * **`open` 이어야 한다.** 하위 스케줄러는 `@DistributedLock` 때문에 CGLIB 프록시로 감싸이는데,
     * 프록시 인스턴스는 필드가 비어 있고 `final` 메서드는 오버라이드되지 않아 위임 없이 프록시에서
     * 그대로 실행된다 — `tx` 가 초기화되지 않았다며 터진다.
     */
    open fun cleanBefore(cutoff: Instant) {
        var count: Int

        do {
            // 수백만 행을 한 트랜잭션에서 지우면 락과 WAL 이 터진다. archive() 와 같은 청크 루프로 나눈다.
            count = tx.execute {
                val targets = repo.findAllByCreatedAtBefore(cutoff, PageRequest.of(0, chunk))
                if (targets.isEmpty()) return@execute 0

                repo.deleteAllInBatch(targets)
                return@execute targets.size
            } ?: 0
        } while (count == chunk)
    }
}
