package com.langlez.chat.infrastructure.jpa

import com.langlez.chat.infrastructure.outbox.ChatOutBoxHistory
import com.langlez.rdb.outbox.OutBoxHistoryRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatOutBoxHistoryRepository : OutBoxHistoryRepository<ChatOutBoxHistory>
