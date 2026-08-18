package com.langlez.wave.config

import com.langlez.wave.domain.WaveSessionRepository
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * 음성방 실시간 채널.
 *
 * `@EnableWebSocketMessageBroker` 를 다시 붙이지 않는다. 그 어노테이션은 브로커 설정을 통째로
 * 가져오는 것이고, 이미 chat 모듈이 켜 뒀다. 스프링은 `WebSocketMessageBrokerConfigurer` 빈을
 * **전부 모아** 위임하므로, 여기서는 이 모듈 몫(엔드포인트 + 구독 인가)만 얹으면 된다.
 * 남의 모듈 파일을 고치지 않고 붙일 수 있는 유일한 지점이기도 하다.
 *
 * CONNECT 시점의 JWT 인증은 chat 쪽 인터셉터가 인바운드 채널 전체에 이미 걸어 놨다.
 * 인증(누구인가)은 채널 공통이고, 인가(이 방을 볼 자격이 있는가)만 모듈마다 다르다.
 */
@Configuration
class WaveWebSocketConfiguration(private val sessions: WaveSessionRepository) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // 모바일 앱 전용이라 SockJS 폴백은 두지 않는다.
        registry.addEndpoint("/ws/wave").setAllowedOriginPatterns("*")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(waveSubscriptionInterceptor())
    }

    /**
     * 구독 인가. 연결만 인증하고 끝내면 로그인한 아무나 `/topic/wave/{남의방}/chat` 을 구독해
     * 남의 대화를 그대로 엿볼 수 있다(인증은 됐지만 인가가 없는 상태).
     *
     * 목적지를 엄격한 정규식으로만 통과시킨다. 심플 브로커는 구독 목적지에 별표 와일드카드를
     * 지원해서, 느슨하게 열어두면 방 번호 자리에 와일드카드를 넣어 전체 방을 한 번에 빨아간다.
     * 그래서 숫자만 허용한다.
     *
     * wave 토픽이 아니면 손대지 않는다 — 같은 채널을 chat 등 다른 모듈이 함께 쓴다.
     */
    fun waveSubscriptionInterceptor() = object : ChannelInterceptor {
        override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
            val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
                ?: return message

            if (accessor.command != StompCommand.SUBSCRIBE) return message

            val destination = accessor.destination ?: return message
            if (!destination.startsWith(ROOM_TOPIC_PREFIX)) return message

            val roomId = ROOM_TOPIC_PATTERN.matchEntire(destination)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
                ?: throw IllegalArgumentException("wave.room.forbidden")

            val memberId = (accessor.user as? UsernamePasswordAuthenticationToken)?.principal as? Long
                ?: throw IllegalArgumentException("auth.unauthorized")

            if (!sessions.isParticipant(roomId, memberId)) throw IllegalArgumentException("wave.room.forbidden")

            return message
        }
    }

    companion object {
        private const val ROOM_TOPIC_PREFIX = "/topic/wave/"
        private val ROOM_TOPIC_PATTERN = Regex("^/topic/wave/(\\d+)/chat$")
    }
}
