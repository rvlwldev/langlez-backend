package com.langlez.relationship.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "relationship_event_outbox")
class RelationshipOutBox(
    aggregateType: String,
    aggregateId: String,
    eventName: String,
    payload: String,
    createdAt: Instant = Instant.now(),
) : OutBox(aggregateType, aggregateId, eventName, payload, createdAt)
