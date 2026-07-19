package com.langlez.chat.infrastructure.outbox.jpa

import com.langlez.chat.infrastructure.outbox.ChatOutBox
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ChatOutBoxJpaRepository : JpaRepository<ChatOutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(
        statuses: List<ChatOutBox.Status>,
        pageable: Pageable,
    ): List<ChatOutBox>
    fun findAllByStatus(status: ChatOutBox.Status): List<ChatOutBox>
}
