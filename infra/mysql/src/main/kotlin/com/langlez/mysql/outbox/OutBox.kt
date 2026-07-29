package com.langlez.mysql.outbox

import jakarta.persistence.Column
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.Instant

@MappedSuperclass
abstract class OutBox(
    val domain: String,
    val topic: String,
    @Column(columnDefinition = "TEXT") val payload: String?,
    @Column(name = "`key`") val key: String?,
    val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0

    @Enumerated(STRING)
    var status: OutBoxStatus = OutBoxStatus.READY
        private set

    var attempts: Int = 0
        private set

    var failedAt: Instant? = null
        private set

    fun dispatch() {
        check(status == OutBoxStatus.READY || status == OutBoxStatus.PROCESSING) { "잘못된 이벤트 '발행' 시도" }
        check(attempts < MAX_ATTEMPTS) { "최대 재시도 횟수 초과" }
        status = OutBoxStatus.PROCESSING
        attempts++
    }

    fun complete() {
        check(status == OutBoxStatus.PROCESSING) { "잘못된 이벤트 '완료' 시도" }
        status = OutBoxStatus.COMPLETE
    }

    fun fail() {
        if (attempts >= MAX_ATTEMPTS) {
            status = OutBoxStatus.FAILED
            failedAt = Instant.now()
        } else {
            status = OutBoxStatus.PROCESSING
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 3
    }
}
