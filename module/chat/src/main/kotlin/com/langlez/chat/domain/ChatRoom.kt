package com.langlez.chat.domain

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
@Table(name = "chat_rooms")
class ChatRoom(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    // 목록 정렬·미리보기를 위해 방에 비정규화해 둔다. 없으면 방마다 마지막 메시지를 다시 조회해야 한다(N+1).
    @Column(name = "last_message_at") var lastMessageAt: Instant? = null,
    @Column(name = "last_message_preview", length = 200) var lastMessagePreview: String? = null,

    @CreatedDate @Column(name = "created_at") val createdAt: Instant = Instant.now(),
) {
    fun onMessage(preview: String, at: Instant) {
        lastMessagePreview = preview.take(200)
        lastMessageAt = at
    }

    /**
     * [at] 의 메시지가 아직 방에 반영되지 않았는가. 메시지(Mongo)와 방 메타(Postgres)가
     * 어긋났는지 판정한다. 한 번도 갱신되지 않은 방(null)은 무조건 뒤처진 것이다.
     */
    fun isBehind(at: Instant): Boolean = lastMessageAt?.isBefore(at) ?: true
}
