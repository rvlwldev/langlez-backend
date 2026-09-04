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

    /**
     * 정지시킨 운영자. **nullable 이다** — 이 컬럼이 생기기 전(V15 이전)에 쌓인 행은
     * 누가 조치했는지 기록 자체가 없어 백필할 값이 없다. 추측해 채우면 감사 기록으로서
     * 오히려 해롭다. 앞으로 들어오는 행은 `MemberWriter.suspend` 가 항상 채운다.
     */
    @Column(name = "actor_id")
    val actorId: Long? = null,

    @Column(name = "is_released")
    var isReleased: Boolean = false
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0

    constructor(member: Member, reason: String?, duration: Duration?, actorId: Long? = null) : this(
        memberId = member.id,
        reason = reason,
        releaseAt = duration?.let { Instant.now().plus(it) },
        actorId = actorId,
    )

    constructor(memberId: Long, reason: String?, duration: Duration?, actorId: Long? = null) : this(
        memberId = memberId,
        reason = reason,
        releaseAt = duration?.let { Instant.now().plus(it) },
        actorId = actorId,
    )

    /**
     * 이력을 닫는다.
     *
     * 정지가 풀리는 모든 경로(어드민 해제·만료 배치)가 반드시 여기를 지나야 한다.
     * 안 닫으면 `isReleased` 가 영영 false 로 남아 만료 배치가 매 주기 같은 행을 다시 잡는다.
     */
    fun release() {
        isReleased = true
    }
}
