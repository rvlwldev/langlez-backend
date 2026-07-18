package com.langlez.chat.api

import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatWebSocketController(
    private val messagingTemplate: SimpMessagingTemplate
) {

    @MessageMapping("/chat/{roomId}/typing", "chat/{roomId}/typing")
    fun handleTyping(
        @DestinationVariable roomId: String,
        @org.springframework.messaging.handler.annotation.Payload payload: Map<String, Any>?,
        principal: Principal
    ) {
        val memberId = principal.name.toLong()
        val typingPayload = TypingEventPayload(memberId = memberId)
        messagingTemplate.convertAndSend("/topic/chat/room/$roomId/typing", typingPayload)
    }
}

data class TypingEventPayload(
    val memberId: Long = 0L
)
