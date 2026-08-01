package com.langlez.relationship.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import com.langlez.rdb.outbox.OutBoxHistory
import com.langlez.rdb.outbox.OutBoxStatus
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "relationship_event_outbox_history")
class RelationshipOutBoxHistory(
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

    constructor(o: RelationshipOutBox) : this(o as OutBox)
}
