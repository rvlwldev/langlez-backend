package com.langlez.chat.infrastructure.jpa

import com.langlez.chat.infrastructure.outbox.ChatOutBox
import com.langlez.rdb.outbox.OutBoxRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatOutBoxRepository : OutBoxRepository<ChatOutBox>
