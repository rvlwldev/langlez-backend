package com.langlez.matching.application

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class MatchBroadcaster(
    private val messagingTemplate: SimpMessagingTemplate,
) {

    fun broadcastMatched(memberId: Long, roomId: String, partnerUsername: String) {
        messagingTemplate.convertAndSend(
            "/topic/matching/$memberId",
            MatchedPayload(status = "MATCHED", roomId = roomId, partnerUsername = partnerUsername)
        )
    }
}

data class MatchedPayload(
    val status: String,
    val roomId: String,
    val partnerUsername: String,
)
