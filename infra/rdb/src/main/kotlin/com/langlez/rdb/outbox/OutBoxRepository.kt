package com.langlez.rdb.outbox

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.NoRepositoryBean

@NoRepositoryBean
interface OutBoxRepository<T : OutBox> : JpaRepository<T, Long> {
    fun fetch(chunk: Int, maxTries: Int): List<T> = findAllByStatusAndTriesLessThanEqualOrderByCreatedAtAsc(
        maxRetries = maxTries,
        limit = PageRequest.of(0, chunk)
    )

    fun fetchProcessed(chunk: Int): List<T> = findAllByCompletedAtIsNotNullOrFailedAtIsNotNull(PageRequest.of(0, chunk))

    fun findAllByStatusAndTriesLessThanEqualOrderByCreatedAtAsc(
        status: OutBox.Status = OutBox.Status.PENDING,
        maxRetries: Int,
        limit: Pageable = PageRequest.of(0, Int.MAX_VALUE)
    ): List<T>

    fun findAllByCompletedAtIsNotNullOrFailedAtIsNotNull(page: Pageable): List<T>
}