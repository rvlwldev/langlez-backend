package com.langlez.wavechat.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "wave_messages",
    indexes = [Index(name = "IDX_WAVE_MESSAGE_ROOM", columnList = "wave_room_id, created_at, id")]
)
class WaveMessage(
    @Column(name = "wave_room_id", nullable = false) val waveRoomId: Long,
    @Column(name = "sender_id", nullable = false) val senderId: Long,
    @Column(columnDefinition = "TEXT", nullable = false) val content: String,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        private set

    fun delete() {
        if (deletedAt == null) {
            deletedAt = Instant.now()
        }
    }

    fun isDeleted(): Boolean = deletedAt != null
}
