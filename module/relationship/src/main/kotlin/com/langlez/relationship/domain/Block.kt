package com.langlez.relationship.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "member_blocks",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_MEMBER_BLOCK", columnNames = ["blocker_id", "blocked_id"])]
)
class Block(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long,

    @Column(name = "blocker_id") val blockerId: Long,
    @Column(name = "blocked_id") val blockedId: Long,

    @CreatedDate @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
) {
    // 생성 시점에 막는다. 자기 차단이 한 행이라도 들어가면 BlockQuery 가 본인과의 상호작용을 통째로 막는다.
    init {
        require(blockerId != blockedId) { "social.block.self" }
    }

    constructor(blockerId: Long, blockedId: Long) : this(
        id = 0,
        blockerId = blockerId,
        blockedId = blockedId,
    )
}
