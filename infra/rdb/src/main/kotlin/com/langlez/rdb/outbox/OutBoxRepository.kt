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
     * 인스턴스가 여러 대면 같은 PENDING 행을 동시에 집어 카프카로 두 번 발행한다.
     * 행 잠금으로 선점하고, 남이 이미 잡은 행은 기다리지 말고 건너뛴다(SKIP LOCKED).
     * 잠금은 트랜잭션 안에서만 유효하므로 호출부는 반드시 트랜잭션 경계 안이어야 한다.
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
