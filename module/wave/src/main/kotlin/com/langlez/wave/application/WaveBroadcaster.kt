package com.langlez.wave.application

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class WaveBroadcaster(
    private val messagingTemplate: SimpMessagingTemplate,
) {

    fun broadcastSignal(roomId: Long, payload: Any) {
        messagingTemplate.convertAndSend("/topic/wave/$roomId/signal", payload)
    }

    fun broadcastChat(roomId: Long, payload: WaveChatBroadcastPayload) {
        messagingTemplate.convertAndSend("/topic/wave/$roomId/chat", payload)
    }

    fun sendErrorToUser(username: String, payload: WaveErrorPayload) {
        messagingTemplate.convertAndSendToUser(username, "/queue/wave/errors", payload)
    }
}

data class WaveChatBroadcastPayload(
    val roomId: Long,
    val senderId: Long,
    val content: String,
)

data class WaveErrorPayload(
    val status: Int,
    val message: String,
)
