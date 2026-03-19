package com.langlez.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "event_outbox_history")
internal class OutBoxHistory(
    @Id val id: Long,
    val aggregateType: String,
    val aggregateId: String,
    val eventName: String,

    @Column(columnDefinition = "TEXT")
    val payload: String,

    val retries: Int,
    val createdAt: Instant,
    val processedAt: Instant = Instant.now()
) {
    constructor(o: OutBox) : this(
        id = o.id,
        aggregateType = o.aggregateType,
        aggregateId = o.aggregateId,
        eventName = o.eventName,
        payload = o.payload,
        retries = o.attempts,
        createdAt = o.createdAt
    )
}