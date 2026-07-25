package com.langlez.mysql.outbox

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.Instant

/**
 * 발행이 끝난 OutBox 레코드를 이관해 두는 히스토리 테이블의 공통 베이스.
 * 원본 OutBox의 id를 그대로 PK로 사용한다.
 */
@MappedSuperclass
abstract class AbstractOutBoxHistory(
    @Id val id: Long,
    val aggregateType: String,
    val aggregateId: String,
    val eventName: String,
    @Column(columnDefinition = "TEXT") val payload: String,
    val attempts: Int,
    val createdAt: Instant,
    val processedAt: Instant = Instant.now(),
)
