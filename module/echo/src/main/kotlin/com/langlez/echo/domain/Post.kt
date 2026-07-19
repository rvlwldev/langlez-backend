package com.langlez.echo.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "posts")
class Post(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Column(columnDefinition = "TEXT", nullable = false)
    var content: String,

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0
        private set

    @Column(name = "report_count", nullable = false)
    var reportCount: Int = 0
        private set

    @Column(nullable = false)
    var blinded: Boolean = false
        private set

    @Column(name = "blinded_at")
    var blindedAt: Instant? = null
        private set

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        private set

    fun delete(now: Instant = Instant.now()) {
        if (this.deletedAt != null) return
        this.deletedAt = now
    }

    companion object {
        const val MAX_CONTENT_LENGTH = 1000
        const val MAX_MEDIA_COUNT = 12
        const val BLIND_THRESHOLD = 5
    }
}
