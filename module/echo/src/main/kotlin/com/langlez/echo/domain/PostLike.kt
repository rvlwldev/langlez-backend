package com.langlez.echo.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "post_likes",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_POST_LIKE", columnNames = ["post_id", "member_id"])]
)
class PostLike(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(name = "post_id", nullable = false)
    val postId: Long,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
