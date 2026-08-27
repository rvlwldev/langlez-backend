package com.langlez.rdb.outbox

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.transaction.annotation.Transactional

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
     * **`@Transactional` 이 반드시 있어야 한다.** Spring Data 의 기본 트랜잭션은 `SimpleJpaRepository`
     * 가 구현하는 CRUD 메서드에만 붙고 파생 쿼리에는 안 붙는다. 트랜잭션 없이 잠금 쿼리를 쏘면
     * 하이버네이트가 `Query requires transaction be in progress` 로 매번 터진다 —
     * 아웃박스 발행이 통째로 멈추는데 스케줄러 로그를 보기 전에는 드러나지 않는다.
     *
     * 주의: 이 잠금만으로는 중복 발행을 막지 못한다. 여기서 연 트랜잭션이 fetch 직후 커밋되면서
     * 잠금이 즉시 풀리기 때문이다. **중복 발행을 실제로 막는 건 하위 스케줄러의 `@DistributedLock` 이다.**
     * `OutBoxProcessor` 를 상속하는 스케줄러는 `@DistributedLock` 을 반드시 붙여야 한다.
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // -2 = SKIP LOCKED
    fun findAllByStatusAndTriesLessThanEqualOrderByCreatedAtAsc(
        status: OutBox.Status = OutBox.Status.PENDING,
        maxRetries: Int,
        limit: Pageable = PageRequest.of(0, Int.MAX_VALUE)
    ): List<T>

    fun findAllByCompletedAtIsNotNullOrFailedAtIsNotNull(page: Pageable): List<T>
}
