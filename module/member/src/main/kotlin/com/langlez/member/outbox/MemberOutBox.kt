package com.langlez.member.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "member_event_outbox")
class MemberOutBox(
    val aggregateType: String,
    val aggregateId: String,
    val eventName: String,
    @Column(columnDefinition = "TEXT") val payload: String,
    val createdAt: Instant = Instant.now(),
) {
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0

    @Enumerated(STRING) var status: Status = Status.READY; private set
    var attempts: Int = 0; private set
    var failedAt: Instant? = null; private set

    enum class Status { READY, PROCESSING, COMPLETE, FAILED }

    fun dispatch() {
        check(status == Status.READY || status == Status.PROCESSING) { "잘못된 이벤트 '발행' 시도" }
        check(attempts < MAX_ATTEMPTS) { "최대 재시도 횟수 초과" }
        status = Status.PROCESSING
        attempts++
    }

    fun complete() {
        check(status == Status.PROCESSING || status == Status.FAILED) { "잘못된 이벤트 '완료' 시도" }
        status = Status.COMPLETE
    }

    fun fail() {
        if (attempts >= MAX_ATTEMPTS) { status = Status.FAILED; failedAt = Instant.now() }
        else status = Status.PROCESSING
    }

    companion object { const val MAX_ATTEMPTS = 3 }
}
