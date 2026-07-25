package com.langlez.echo.infrastructure.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "echo_event_outbox_history")
class EchoOutBoxHistory(
    @Id val id: Long,
    val aggregateType: String,
    val aggregateId: String,
    val eventName: String,
    @Column(columnDefinition = "TEXT") val payload: String,
    val attempts: Int,
    val createdAt: Instant,
    val processedAt: Instant = Instant.now(),
) {
    constructor(o: EchoOutBox) : this(
        id = o.id,
        aggregateType = o.aggregateType,
        aggregateId = o.aggregateId,
        eventName = o.eventName,
        payload = o.payload,
        attempts = o.attempts,
        createdAt = o.createdAt,
    )
}
