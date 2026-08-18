package com.langlez.relationship.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import com.langlez.rdb.outbox.OutBoxHistory
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "relationship_event_outbox_history",
    indexes = [Index(name = "IDX_RELATIONSHIP_OUTBOX_DOMAIN", columnList = "domain")]
)
class RelationshipOutBoxHistory(outbox: OutBox) : OutBoxHistory(outbox)
