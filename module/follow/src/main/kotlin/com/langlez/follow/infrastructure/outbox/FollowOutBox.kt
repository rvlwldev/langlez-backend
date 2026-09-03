package com.langlez.follow.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "follow_event_outbox")
class FollowOutBox(
    domain: String,
    topic: String,
    payload: String,
    key: String? = null
) : OutBox(domain, topic, payload, key)
