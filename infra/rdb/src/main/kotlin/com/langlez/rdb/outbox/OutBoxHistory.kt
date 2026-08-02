package com.langlez.rdb.outbox

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
    val tries: Int,
    @Enumerated(STRING) val status: OutBox.Status,
    val createdAt: Instant,
    val completedAt: Instant?,
) {
    constructor(o: OutBox) : this(
        id = o.id,
        domain = o.domain,
        topic = o.topic,
        payload = o.payload,
        key = o.key,
        tries = o.tries,
        status = o.status,
        createdAt = o.createdAt,
        completedAt = o.completedAt
    )
}
