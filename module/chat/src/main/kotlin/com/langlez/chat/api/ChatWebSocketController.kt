package com.langlez.chat.api

import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.core.LanglezException
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatWebSocketController(
    private val messagingTemplate: SimpMessagingTemplate,
    private val chatRoomRepository: ChatRoomRepository,
) {

    @MessageMapping("/chat/{roomId}/typing", "chat/{roomId}/typing")
    fun handleTyping(
        @DestinationVariable roomId: String,
        @org.springframework.messaging.handler.annotation.Payload payload: Map<String, Any>?,
        principal: Principal
    ) {
        val memberId = principal.name.toLong()
        val room = chatRoomRepository.findById(roomId)
            ?: throw LanglezException(404, "chat.room-not-found")
        if (!room.hasParticipant(memberId)) {
            throw LanglezException(403, "chat.room-forbidden")
        }

        val typingPayload = TypingEventPayload(memberId = memberId)
        messagingTemplate.convertAndSend("/topic/chat/room/$roomId/typing", typingPayload)
    }
}

data class TypingEventPayload(
    val memberId: Long = 0L
)
