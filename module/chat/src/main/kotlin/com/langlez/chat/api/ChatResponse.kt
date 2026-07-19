package com.langlez.chat.api

import com.langlez.chat.domain.ChatMessage
import java.time.Instant

class ChatResponse {
    data class RoomSummary(
        val id: String,
        val targetUsername: String,
        val targetNickname: String,
        val lastMessageAt: Instant?,
        val lastMessagePreview: String?,
        val unreadCount: Long,
        val createdAt: Instant
    )

    data class RoomCursorList(
        val nextCursor: String?,
        val rooms: List<RoomSummary>
    )

    data class ReplyPreview(
        val messageId: String,
        val senderUsername: String,
        val type: ChatMessage.Type,
        val contentPreview: String?,
        val deleted: Boolean
    )

    data class MessageSummary(
        val id: String,
        val senderUsername: String,
        val type: ChatMessage.Type,
        val content: String?,
        val fileUrl: String?,
        val createdAt: Instant,
        val replyPreview: ReplyPreview? = null,
        val deleted: Boolean = false
    )

    data class MessageCursorList(
        val nextCursor: String?,
        val messages: List<MessageSummary>
    )
}
