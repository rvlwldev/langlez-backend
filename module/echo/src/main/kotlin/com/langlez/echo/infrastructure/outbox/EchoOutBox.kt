package com.langlez.echo.infrastructure.outbox

import com.langlez.mysql.outbox.OutBox
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "echo_event_outbox")
class EchoOutBox(
    aggregateType: String,
    aggregateId: String,
    eventName: String,
    payload: String,
    createdAt: Instant = Instant.now(),
) : OutBox(aggregateType, aggregateId, eventName, payload, createdAt)
