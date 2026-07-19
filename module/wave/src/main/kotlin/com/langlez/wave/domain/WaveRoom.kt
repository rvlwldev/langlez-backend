package com.langlez.wave.domain

import com.langlez.core.LanglezException
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
        if (title.isBlank()) {
            throw LanglezException(400, "wave.invalid-title")
        }
        if (maxParticipants !in MIN_PARTICIPANTS..MAX_PARTICIPANTS) {
            throw LanglezException(400, "wave.invalid-max-participants")
        }
    }

    fun isEnded(): Boolean = endedAt != null

    fun end(now: Instant = Instant.now()) {
        if (endedAt == null) {
            endedAt = now
        }
    }

    fun updateTitle(newTitle: String) {
        if (isEnded()) {
            throw LanglezException(409, "wave.already-ended")
        }
        if (newTitle.isBlank()) {
            throw LanglezException(400, "wave.invalid-title")
        }
        this.title = newTitle
    }

    companion object {
        const val MIN_PARTICIPANTS = 4
        const val MAX_PARTICIPANTS = 8
    }
}
