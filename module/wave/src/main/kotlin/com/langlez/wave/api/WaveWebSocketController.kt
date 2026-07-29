package com.langlez.wave.api

import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.MemberRole
import com.langlez.wave.application.WaveBroadcaster
import com.langlez.wave.application.WaveChatBroadcastPayload
import com.langlez.wave.application.WaveErrorPayload
import com.langlez.wave.config.WaveStompPrincipal
import com.langlez.wavechat.application.WaveChatService
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
    private val waveChatService: WaveChatService,
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
        val stompPrincipal = principal as? WaveStompPrincipal
        val memberId = stompPrincipal?.memberId ?: principal.name.toLongOrNull() ?: return
        val role = stompPrincipal?.role ?: memberRepository.findById(memberId)?.role ?: return

        if (role == MemberRole.MEMBER) {
            val cooldownKey = "wave:chat-cooldown:$roomId:$memberId"
            val acquired = redissonClient.getBucket<String>(cooldownKey)
                .setIfAbsent("1", Duration.ofSeconds(30))
            if (!acquired) {
                broadcaster.sendErrorToUser(memberId.toString(), WaveErrorPayload(429, "wave.chat-cooldown"))
                return
            }
        }

        val savedMessage = waveChatService.sendMessage(roomId, memberId, payload.content)

        broadcaster.broadcastChat(roomId, WaveChatBroadcastPayload(roomId, memberId, savedMessage.content))
    }
}

data class WaveChatMessageRequest(
    val content: String
)
