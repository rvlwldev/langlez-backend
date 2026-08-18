package com.langlez.rdb.outbox

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.NoRepositoryBean

@NoRepositoryBean
interface OutBoxRepository<T : OutBox> : JpaRepository<T, Long> {
    fun fetch(chunk: Int, maxTries: Int): List<T> = findAllByStatusAndTriesLessThanEqualOrderByCreatedAtAsc(
        maxRetries = maxTries,
        limit = PageRequest.of(0, chunk)
    )

    fun fetchProcessed(chunk: Int): List<T> = findAllByCompletedAtIsNotNullOrFailedAtIsNotNull(PageRequest.of(0, chunk))

    /**
     * 행 잠금으로 선점하고, 남이 이미 잡은 행은 기다리지 말고 건너뛴다(SKIP LOCKED).
     *
     * 주의: 이 잠금만으로는 중복 발행을 막지 못한다. `OutBoxProcessor.send()` 에 트랜잭션이 없어
     * Spring Data 가 만든 조회 트랜잭션이 fetch 직후 커밋되면서 잠금이 즉시 풀린다.
     * **중복 발행을 실제로 막는 건 하위 스케줄러의 `@DistributedLock` 이다.**
     * `OutBoxProcessor` 를 상속하는 스케줄러는 `@DistributedLock` 을 반드시 붙여야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // -2 = SKIP LOCKED
    fun findAllByStatusAndTriesLessThanEqualOrderByCreatedAtAsc(
        status: OutBox.Status = OutBox.Status.PENDING,
        maxRetries: Int,
        limit: Pageable = PageRequest.of(0, Int.MAX_VALUE)
    ): List<T>

    fun findAllByCompletedAtIsNotNullOrFailedAtIsNotNull(page: Pageable): List<T>
}
