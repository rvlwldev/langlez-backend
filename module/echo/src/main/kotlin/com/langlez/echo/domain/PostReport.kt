package com.langlez.echo.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "post_reports",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_POST_REPORT", columnNames = ["post_id", "reporter_id"])]
)
class PostReport(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(name = "post_id", nullable = false)
    val postId: Long,

    @Column(name = "reporter_id", nullable = false)
    val reporterId: Long,

    @Column(nullable = false)
    val reason: String,

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
