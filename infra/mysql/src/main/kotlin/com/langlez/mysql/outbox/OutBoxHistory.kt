package com.langlez.mysql.outbox

import jakarta.persistence.Column
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.Instant

@MappedSuperclass
abstract class OutBoxHistory(
    @Id val id: Long,
    val domain: String,
    val topic: String,
    @Column(columnDefinition = "TEXT") val payload: String?,
    @Column(name = "`key`") val key: String?,
    val attempts: Int,
    @Enumerated(STRING) val status: OutBoxStatus,
    val createdAt: Instant,
    val processedAt: Instant = Instant.now(),
) {
    constructor(o: OutBox) : this(
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
