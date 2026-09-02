package com.langlez.block.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// 테이블 이름은 member_blocks 그대로다. 모듈이 갈렸다고 테이블을 옮기면 데이터 마이그레이션이
// 따라붙는다. 이름 정리는 별도 작업이다.
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "member_blocks",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_MEMBER_BLOCK", columnNames = ["blocker_id", "blocked_id"])],
    // 차단 목록도 커서 페이징이다. 양방향 차단 판정은 두 컬럼 등치라 유니크 인덱스로 충분하다.
    indexes = [Index(name = "IDX_MEMBER_BLOCK_BLOCKER", columnList = "blocker_id, id DESC")]
)
class Block(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long,

    @Column(name = "blocker_id") val blockerId: Long,
    @Column(name = "blocked_id") val blockedId: Long,

    @CreatedDate @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
) {
    // 생성 시점에 막는다. 자기 차단이 한 행이라도 들어가면 BlockReader 가 본인과의 상호작용을 통째로 막는다.
    init {
        require(blockerId != blockedId) { "social.block.self" }
    }

    constructor(blockerId: Long, blockedId: Long) : this(
        id = 0,
        blockerId = blockerId,
        blockedId = blockedId,
    )
}
