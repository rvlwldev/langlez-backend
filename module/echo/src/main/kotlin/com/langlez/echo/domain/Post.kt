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

        /** i18n 문구(`echo.post.media-limit-exceeded`)가 "최대 4개"로 고정돼 있어 상한도 4 로 맞춘다.
         * 첨부 확정은 key 하나당 스토리지 왕복 1회라(`EchoPostCreateRequest` 참고), 개수가 늘수록
         * `createPost` 요청 하나가 순차 블로킹 I/O 를 그만큼 더 물고 간다. */
        const val MAX_MEDIA_COUNT = 4
        const val BLIND_THRESHOLD = 5
    }
}
