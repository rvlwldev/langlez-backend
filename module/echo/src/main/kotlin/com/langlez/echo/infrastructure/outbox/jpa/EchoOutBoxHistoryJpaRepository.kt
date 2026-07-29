package com.langlez.echo.infrastructure.outbox.jpa

import com.langlez.echo.infrastructure.outbox.EchoOutBoxHistory
import java.time.Instant
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface EchoOutBoxHistoryJpaRepository : JpaRepository<EchoOutBoxHistory, Long> {
    fun findByCreatedAtBeforeOrderByCreatedAtAsc(cutoff: Instant, pageable: Pageable): List<EchoOutBoxHistory>
}
