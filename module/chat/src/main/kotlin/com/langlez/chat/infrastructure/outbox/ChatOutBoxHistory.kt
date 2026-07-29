package com.langlez.chat.infrastructure.outbox

import com.langlez.mysql.outbox.OutBox
import com.langlez.mysql.outbox.OutBoxHistory
import com.langlez.mysql.outbox.OutBoxStatus
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "chat_event_outbox_history")
class ChatOutBoxHistory(
    id: Long,
    domain: String,
    topic: String,
    payload: String?,
    key: String?,
    attempts: Int,
    status: OutBoxStatus,
    createdAt: Instant,
    processedAt: Instant = Instant.now(),
) : OutBoxHistory(id, domain, topic, payload, key, attempts, status, createdAt, processedAt) {

    constructor(o: ChatOutBox) : this(o as OutBox)
}
