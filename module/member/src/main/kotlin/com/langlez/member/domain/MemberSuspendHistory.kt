package com.langlez.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant

@Entity
@Table(
    name = "member_suspend_history",
    indexes = [Index(name = "IDX_MEMBER_SUSPEND_RELEASED", columnList = "id, is_released")]
)
class MemberSuspendHistory(
    @Id
    val id: Long,
    val suspendedAt: Instant = Instant.now(),
    val reason: String? = null,
    val releaseAt: Instant? = null,

    @Column(name = "is_released")
    var isReleased: Boolean = false
) {
    constructor(member: Member, reason: String?, duration: Duration?) : this(
        id = member.id,
        reason = reason,
        releaseAt = duration?.let { Instant.now().plus(it) }
    )

    constructor(id: Long, reason: String?, duration: Duration?) : this(
        id = id,
        reason = reason,
        releaseAt = duration?.let { Instant.now().plus(it) }
    )
}