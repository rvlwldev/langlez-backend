package com.langlez.wave.api

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.wave.application.WaveBroadcaster
import com.langlez.wave.application.WaveChatBroadcastPayload
import com.langlez.wave.application.WaveErrorPayload
import org.redisson.api.RedissonClient
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller
import java.security.Principal
import java.time.Duration

@Controller
class WaveWebSocketController(
    private val broadcaster: WaveBroadcaster,
    private val memberRepository: MemberRepository,
    private val redissonClient: RedissonClient,
) {

    @MessageMapping("/wave/{roomId}/signal")
    fun handleSignal(
        @DestinationVariable roomId: Long,
        @Payload payload: Map<String, Any>?,
        principal: Principal
    ) {
        // SDP offer/answer, ICE candidate 등 opaque payload를 그대로 다른 참가자에게 중계한다.
        broadcaster.broadcastSignal(roomId, payload ?: emptyMap<String, Any>())
    }

    @MessageMapping("/wave/{roomId}/chat")
    fun handleChat(
        @DestinationVariable roomId: Long,
        @Payload payload: WaveChatMessageRequest,
        principal: Principal
    ) {
        val memberId = principal.name.toLong()
        val member = memberRepository.findById(memberId) ?: return

        if (member.role == Member.Role.MEMBER) {
            val cooldownKey = "wave:chat-cooldown:$roomId:$memberId"
            val acquired = redissonClient.getBucket<String>(cooldownKey)
                .setIfAbsent("1", Duration.ofSeconds(30))
            if (!acquired) {
                broadcaster.sendErrorToUser(memberId.toString(), WaveErrorPayload(429, "wave.chat-cooldown"))
                return
            }
        }

        broadcaster.broadcastChat(roomId, WaveChatBroadcastPayload(roomId, memberId, payload.content))
    }
}

data class WaveChatMessageRequest(
    val content: String
)
