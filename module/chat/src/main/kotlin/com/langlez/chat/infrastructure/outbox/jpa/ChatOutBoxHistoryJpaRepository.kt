package com.langlez.chat.infrastructure.outbox.jpa

import com.langlez.chat.infrastructure.outbox.ChatOutBoxHistory
import org.springframework.data.jpa.repository.JpaRepository

interface ChatOutBoxHistoryJpaRepository : JpaRepository<ChatOutBoxHistory, Long>
