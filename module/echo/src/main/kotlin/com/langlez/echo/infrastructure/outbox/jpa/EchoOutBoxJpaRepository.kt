package com.langlez.echo.infrastructure.outbox.jpa

import com.langlez.echo.infrastructure.outbox.EchoOutBox
import com.langlez.rdb.outbox.OutBoxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface EchoOutBoxJpaRepository : JpaRepository<EchoOutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(
        statuses: List<OutBoxStatus>,
        pageable: Pageable,
    ): List<EchoOutBox>

    fun findAllByStatusIn(statuses: List<OutBoxStatus>, pageable: Pageable): List<EchoOutBox>
}
