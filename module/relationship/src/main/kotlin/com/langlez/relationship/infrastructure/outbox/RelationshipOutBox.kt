package com.langlez.relationship.infrastructure.outbox

import com.langlez.mysql.outbox.AbstractOutBox
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
) : AbstractOutBox(aggregateType, aggregateId, eventName, payload, createdAt)
