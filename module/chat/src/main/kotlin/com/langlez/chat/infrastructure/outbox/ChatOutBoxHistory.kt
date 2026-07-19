package com.langlez.chat.infrastructure.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "chat_event_outbox_history")
class ChatOutBoxHistory(
    @Id val id: Long,
    val aggregateType: String,
    val aggregateId: String,
    val eventName: String,
    @Column(columnDefinition = "TEXT") val payload: String,
    val retries: Int,
    val createdAt: Instant,
    val processedAt: Instant = Instant.now(),
) {
    constructor(o: ChatOutBox) : this(
        id = o.id,
        aggregateType = o.aggregateType,
        aggregateId = o.aggregateId,
        eventName = o.eventName,
        payload = o.payload,
        retries = o.attempts,
        createdAt = o.createdAt,
    )
}
