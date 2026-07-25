package com.langlez.mysql.outbox

import jakarta.persistence.Column
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.Instant

/**
 * 도메인 모듈별 OutBox 엔티티의 공통 베이스.
 *
 * OutBox는 도메인 개념이 아니라 기술적 장치이므로 공용 도메인 모듈을 두지 않고,
 * 각 모듈이 자기 테이블(`@Entity` + `@Table`)을 소유한 채 여기서 상태 전이 로직만 상속한다.
 *
 * 상태 전이: READY → PROCESSING → COMPLETE, 재시도 소진 시 PROCESSING → FAILED.
 */
@MappedSuperclass
abstract class AbstractOutBox(
    val aggregateType: String,
    val aggregateId: String,
    val eventName: String,
    @Column(columnDefinition = "TEXT") val payload: String,
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

    /** FAILED에서 되살아나지 않도록 PROCESSING에서만 완료를 허용한다. */
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

enum class OutBoxStatus { READY, PROCESSING, COMPLETE, FAILED }
