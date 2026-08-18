package com.langlez.relationship.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "member_follows",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_MEMBER_FOLLOW", columnNames = ["follower_id", "followed_id"])]
)
class Follow(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long,

    @Column(name = "follower_id") val followerId: Long,
    @Column(name = "followed_id") val followedId: Long,

    @CreatedDate @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
) {
    // 생성 시점에 막는다. 서비스마다 검사하면 새 호출 경로에서 빠뜨린다.
    // (noarg 플러그인이 만드는 JPA 전용 생성자는 초기화 블록을 타지 않으므로 하이드레이션엔 영향이 없다)
    init {
        require(followerId != followedId) { "social.follow.self" }
    }

    constructor(followerId: Long, followedId: Long) : this(
        id = 0,
        followerId = followerId,
        followedId = followedId,
    )
}
