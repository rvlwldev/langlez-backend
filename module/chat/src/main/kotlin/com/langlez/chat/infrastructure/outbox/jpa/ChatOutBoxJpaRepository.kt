package com.langlez.chat.infrastructure.outbox.jpa

import com.langlez.chat.infrastructure.outbox.ChatOutBox
import com.langlez.rdb.outbox.OutBoxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ChatOutBoxJpaRepository : JpaRepository<ChatOutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(
        statuses: List<OutBoxStatus>,
        pageable: Pageable,
    ): List<ChatOutBox>

    fun findAllByStatusIn(statuses: List<OutBoxStatus>, pageable: Pageable): List<ChatOutBox>
}
