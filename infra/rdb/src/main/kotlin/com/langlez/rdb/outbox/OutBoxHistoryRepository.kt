package com.langlez.rdb.outbox

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.NoRepositoryBean
import java.time.Instant

@NoRepositoryBean
interface OutBoxHistoryRepository<H : OutBoxHistory> : JpaRepository<H, Long> {
    fun findAllByCreatedAtBefore(cutoff: Instant, page: Pageable): List<H>
}
