package com.langlez.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant

/**
 * 정지 이력. 한 회원이 여러 번 정지될 수 있으므로 회원 ID 를 PK 로 쓰지 않는다.
 * (회원 ID 를 PK 로 두면 재정지 때 이전 이력을 덮어쓰고, 매 저장마다 merge 로 SELECT 가 한 번 더 나간다.)
 */
@Entity
@Table(
    name = "member_suspend_history",
    indexes = [Index(name = "IDX_MEMBER_SUSPEND_RELEASED", columnList = "member_id, is_released")]
)
class MemberSuspendHistory(
    @Column(name = "member_id")
    val memberId: Long,
    val suspendedAt: Instant = Instant.now(),
    val reason: String? = null,
    val releaseAt: Instant? = null,

    @Column(name = "is_released")
    var isReleased: Boolean = false
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0

    constructor(member: Member, reason: String?, duration: Duration?) : this(
        memberId = member.id,
        reason = reason,
        releaseAt = duration?.let { Instant.now().plus(it) }
    )

    constructor(memberId: Long, reason: String?, duration: Duration?) : this(
        memberId = memberId,
        reason = reason,
        releaseAt = duration?.let { Instant.now().plus(it) }
    )
}
