package com.langlez.wave.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * 음성 라운지.
 *
 * 방의 생명주기(누가 열었고 언제 끝났는가)만 영속한다. 방 안에서 오간 대화와 지금 누가 있는지는
 * 레디스에만 있고 방이 끝나면 사라진다(`WaveSessionRepository`).
 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "wave_rooms")
class WaveRoom(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(name = "broadcaster_id", nullable = false)
    val broadcasterId: Long,

    @Column(name = "title", nullable = false)
    var title: String = "Untitled Room",

    @Column(name = "max_participants", nullable = false)
    val maxParticipants: Int = 4,

    @CreatedDate
    @Column(name = "started_at", nullable = false)
    val startedAt: Instant = Instant.now(),

    @Column(name = "ended_at")
    var endedAt: Instant? = null,
) {
    init {
        require(title.isNotBlank()) { "wave.title.invalid" }
        require(maxParticipants in MIN_PARTICIPANTS..MAX_PARTICIPANTS) { "wave.max-participants.invalid" }
    }

    fun isEnded(): Boolean = endedAt != null

    fun end(now: Instant = Instant.now()) {
        if (endedAt == null) {
            endedAt = now
        }
    }

    fun updateTitle(newTitle: String) {
        require(!isEnded()) { "wave.room.already-ended" }
        require(newTitle.isNotBlank()) { "wave.title.invalid" }

        this.title = newTitle
    }

    companion object {
        const val MIN_PARTICIPANTS = 4
        const val MAX_PARTICIPANTS = 8
    }
}
