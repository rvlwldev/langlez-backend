package com.langlez.chat.domain

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "chat_rooms")
data class ChatRoom(
    @Id val id: String? = null,
    val participantIds: List<Long>,  // Exactly 2 member IDs, sorted to maintain consistency
    var lastMessageAt: Instant? = null,
    var lastMessagePreview: String? = null,
    val readStatus: MutableMap<Long, Instant> = mutableMapOf(),  // memberId -> lastReadAt
    @CreatedDate val createdAt: Instant = Instant.now(),
) {
    fun updateReadStatus(memberId: Long, readAt: Instant) {
        readStatus[memberId] = readAt
    }

    fun updateLastMessage(preview: String, at: Instant) {
        lastMessagePreview = preview
        lastMessageAt = at
    }

    fun hasParticipant(memberId: Long): Boolean {
        return participantIds.contains(memberId)
    }

    fun getRecipientId(senderId: Long): Long {
        return participantIds.first { it != senderId }
    }
}
