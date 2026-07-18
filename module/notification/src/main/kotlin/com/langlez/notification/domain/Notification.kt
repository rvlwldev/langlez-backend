package com.langlez.notification.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "notifications")
class Notification(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(name = "recipient_id", nullable = false)
    val recipientId: Long,

    @Column(nullable = false)
    val type: String,

    @Column(nullable = false)
    val title: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    val body: String,

    @Column(name = "is_read", nullable = false)
    var read: Boolean = false,

    @Column(columnDefinition = "TEXT", nullable = true)
    val data: String? = null,

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
