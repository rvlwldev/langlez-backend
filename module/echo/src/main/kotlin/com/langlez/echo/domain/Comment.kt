package com.langlez.echo.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
// Post 와 같은 이유로 인덱스 선언이 없다. 실제 DDL 은 V18 이 만든다
// (IDX_COMMENT_POST (post_id, id) where deleted_at is null).
@Table(name = "comments")
class Comment(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(name = "post_id", nullable = false)
    val postId: Long,

    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Column(columnDefinition = "TEXT", nullable = false)
    val content: String,

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        private set

    fun delete(now: Instant = Instant.now()) {
        if (this.deletedAt != null) return
        this.deletedAt = now
    }

    companion object {
        const val MAX_CONTENT_LENGTH = 500
    }
}
