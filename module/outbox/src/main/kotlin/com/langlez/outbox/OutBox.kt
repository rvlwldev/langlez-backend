package com.langlez.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import java.time.Instant

@Entity
@Table(name = "event_outbox")
internal class OutBox(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,
    val aggregateType: String,
    val aggregateId: String,
    val eventName: String,

    @Column(columnDefinition = "TEXT")
    val payload: String,

    @CreatedDate
    val createdAt: Instant = Instant.now(),
) {
    @Enumerated(STRING)
    var status: Status = Status.READY
        private set
    var attempts: Int = 0
        private set
    var failedAt: Instant? = null
        private set

    enum class Status { READY, PROCESSING, COMPLETE, FAILED }

    constructor(type: String, id: String, name: String, payload: String) : this(
        aggregateType = type,
        aggregateId = id,
        eventName = name,
        payload = payload,
    )

    fun dispatch() {
        check(status == Status.READY || status == Status.PROCESSING) { "잘못된 이벤트 '발행' 시도" }
        check(attempts < MAX_ATTEMPTS) { "최대 재시도 횟수 초과" }

        this.status = Status.PROCESSING
        this.attempts++
    }

    fun complete() {
        check(status == Status.PROCESSING || status == Status.FAILED) { "잘못된 이벤트 '완료' 시도" }
        this.status = Status.COMPLETE
    }

    fun fail() {
        if (attempts > MAX_ATTEMPTS) {
            status = Status.FAILED
            failedAt = Instant.now()
        } else {
            status = Status.PROCESSING
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 3
    }
}