package com.langlez.chat.infrastructure.outbox

import com.langlez.mysql.outbox.AbstractOutBoxHistory
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "chat_event_outbox_history")
class ChatOutBoxHistory(
    id: Long,
    aggregateType: String,
    aggregateId: String,
    eventName: String,
    payload: String,
    attempts: Int,
    createdAt: Instant,
    processedAt: Instant = Instant.now(),
) : AbstractOutBoxHistory(id, aggregateType, aggregateId, eventName, payload, attempts, createdAt, processedAt) {

    constructor(o: ChatOutBox) : this(
        id = o.id,
        aggregateType = o.aggregateType,
        aggregateId = o.aggregateId,
        eventName = o.eventName,
        payload = o.payload,
        attempts = o.attempts,
        createdAt = o.createdAt,
    )
}
