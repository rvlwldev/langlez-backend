package com.langlez.rdb.outbox

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
    key: Any? = null,
    val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0

    @Column(name = "`key`")
    val key: String? = key?.toString()

    @Enumerated(STRING)
    var status: Status = Status.PENDING
        protected set

    var tries: Int = 0
        protected set

    var completedAt: Instant? = null
        protected set

    var failedAt: Instant? = null
        protected set

    fun dispatch() {
        check(status == Status.PENDING) { "잘못된 이벤트 '발행' 시도" }
        tries++
    }

    fun complete() {
        check(status == Status.PENDING) { "잘못된 이벤트 '완료' 시도" }
        status = Status.COMPLETE
        completedAt = Instant.now()
    }

    fun fail(maxRetries: Int) {
        if (this.tries >= maxRetries) {
            status = Status.FAILED
            failedAt = Instant.now()
        } else {
            status = Status.PENDING
        }
    }

    enum class Status { PENDING, COMPLETE, FAILED }
}