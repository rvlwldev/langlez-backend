package com.langlez.chat.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "chat_messages")
data class ChatMessage(
    @Id val id: String? = null,
    val roomId: String,
    val senderId: Long,
    val type: Type,
    val content: String? = null,   // Present when type is TEXT
    val fileUrl: String? = null,   // Present when type is IMAGE/VIDEO/AUDIO
    val replyToMessageId: String? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
) {
    enum class Type { TEXT, IMAGE, VIDEO, AUDIO }
}
