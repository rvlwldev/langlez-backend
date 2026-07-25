package com.langlez.echo.infrastructure.outbox.jpa

import com.langlez.echo.infrastructure.outbox.EchoOutBox
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface EchoOutBoxJpaRepository : JpaRepository<EchoOutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(
        statuses: List<EchoOutBox.Status>,
        pageable: Pageable,
    ): List<EchoOutBox>
    fun findAllByStatus(status: EchoOutBox.Status): List<EchoOutBox>
    fun findAllByStatusIn(statuses: List<EchoOutBox.Status>, pageable: Pageable): List<EchoOutBox>
}
