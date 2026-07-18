package com.langlez.chat.domain

import java.time.Instant

interface ChatMessageRepository {
    fun save(message: ChatMessage): ChatMessage
    fun findByRoom(roomId: String, cursor: String?, size: Int): List<ChatMessage>
    fun countUnread(roomId: String, memberId: Long, after: Instant): Long
    fun findAttachments(cursor: String?, size: Int): List<ChatMessage>
    fun findByRoomSince(roomId: String, since: Instant): List<ChatMessage>
}
