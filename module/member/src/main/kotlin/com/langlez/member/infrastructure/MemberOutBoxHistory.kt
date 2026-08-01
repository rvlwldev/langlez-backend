package com.langlez.member.infrastructure

import com.langlez.rdb.outbox.OutBoxHistory
import com.langlez.rdb.outbox.OutBoxStatus
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "member_event_outbox_history", indexes = [Index(name = "", columnList = "domain")])
class MemberOutBoxHistory(
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

    constructor(o: MemberOutBox) : this(
        id = o.id,
        domain = o.domain,
        topic = o.topic,
        payload = o.payload,
        key = o.key,
        attempts = o.attempts,
        status = o.status,
        createdAt = o.createdAt,
    )
}
