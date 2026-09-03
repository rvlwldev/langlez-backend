package com.langlez.block.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "block_event_outbox")
class BlockOutBox(
    domain: String,
    topic: String,
    payload: String,
    key: String? = null
) : OutBox(domain, topic, payload, key)
