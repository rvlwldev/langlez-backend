package com.langlez.chat.domain

import java.time.Instant

interface ChatRoomRepository {
    fun findByParticipants(memberId1: Long, memberId2: Long): ChatRoom?
    fun save(room: ChatRoom): ChatRoom
    fun findByParticipant(memberId: Long, cursor: String?, size: Int): List<ChatRoom>
    fun findAllRooms(cursor: String?, size: Int): List<ChatRoom>
    fun updateReadStatus(roomId: String, memberId: Long, readAt: Instant)
    fun updateLastMessageAndReadStatus(roomId: String, preview: String, at: Instant, memberId: Long)
    fun findById(roomId: String): ChatRoom?
}
