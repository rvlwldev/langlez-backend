package com.langlez.chat.application

import com.langlez.chat.domain.ChatMessage
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ChatBroadcaster(
    private val messagingTemplate: SimpMessagingTemplate
) {

    fun broadcastMessage(roomId: String, messagePayload: ChatMessageBroadcastPayload) {
        messagingTemplate.convertAndSend("/topic/chat/room/$roomId", messagePayload)
    }

    fun broadcastRead(roomId: String, username: String, readAt: Instant) {
        val payload = ReadEventPayload(username = username, readAt = readAt)
        messagingTemplate.convertAndSend("/topic/chat/room/$roomId/read", payload)
    }

    fun broadcastMessageDeleted(roomId: String, messageId: String) {
        val payload = MessageDeletedPayload(messageId = messageId, roomId = roomId)
        messagingTemplate.convertAndSend("/topic/chat/room/$roomId/deleted", payload)
    }
}

data class ChatMessageBroadcastPayload(
    val id: String?,
    val roomId: String,
    val senderUsername: String,
    val type: ChatMessage.Type,
    val content: String?,
    val fileUrl: String?,
    val createdAt: Instant
)

data class ReadEventPayload(
    val username: String,
    val readAt: Instant
)

data class MessageDeletedPayload(
    val messageId: String,
    val roomId: String
)
