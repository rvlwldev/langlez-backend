package com.langlez.member.infrastructure

import com.langlez.rdb.outbox.OutBox
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "member_event_outbox")
class MemberOutBox(
    domain: String,
    topic: String,
    payload: String,
    key: String? = null,
    createdAt: Instant = Instant.now(),
) : OutBox(domain, topic, payload, key, createdAt)
