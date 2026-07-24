package com.langlez.chat.domain

import java.time.Instant

interface ChatMessageRepository {
    fun save(message: ChatMessage): ChatMessage
    fun findById(id: String): ChatMessage?
    fun findByIds(ids: List<String>): List<ChatMessage>
    fun findByRoom(roomId: String, cursor: String?, size: Int): List<ChatMessage>
    fun countUnread(roomId: String, memberId: Long, after: Instant): Long
    fun countUnreadBatch(rooms: List<ChatRoom>, memberId: Long): Map<String, Long>
    fun findByRoomSince(roomId: String, since: Instant): List<ChatMessage>
    fun markDeleted(messageId: String, deletedAt: Instant)
    fun findLastMessage(roomId: String): ChatMessage?
}
