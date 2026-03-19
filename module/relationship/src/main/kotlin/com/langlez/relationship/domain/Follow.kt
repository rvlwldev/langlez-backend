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
    constructor(followerId: Long, followedId: Long) : this(
        id = 0,
        followerId = followerId,
        followedId = followedId,
    )
}
