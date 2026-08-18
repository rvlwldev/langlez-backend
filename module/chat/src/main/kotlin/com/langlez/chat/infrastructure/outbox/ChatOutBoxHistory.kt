package com.langlez.chat.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import com.langlez.rdb.outbox.OutBoxHistory
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(name = "chat_event_outbox_history", indexes = [Index(name = "IDX_CHAT_OUTBOX_DOMAIN", columnList = "domain")])
class ChatOutBoxHistory(outbox: OutBox) : OutBoxHistory(outbox)
