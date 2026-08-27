package com.langlez.chat.config

import com.langlez.config.WebSocketSubscriptionGate
import com.langlez.core.OnlineTracker
import com.langlez.core.TokenBlacklist
import com.langlez.utility.JwtTokenProvider
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.messaging.SessionDisconnectEvent

/**
 * 채팅 실시간 채널.
 *
 * 브로커는 인메모리(`enableSimpleBroker`)다. 이건 자기 JVM 에 붙은 세션에만 전달하므로,
 * 인스턴스가 여러 대면 다른 서버에 붙은 상대가 메시지를 못 받는다.
 * 그 간극은 `RedisMessageBroadcaster` 가 레디스 pub/sub 으로 메운다 —
 * 발행은 레디스로 나가고, 모든 인스턴스가 구독해 각자 자기 세션에 밀어준다.
 * 그래서 서비스 코드는 `SimpMessagingTemplate` 이 아니라 `MessageBroadcaster` 포트를 써야 한다.
 */
@Configuration
@EnableWebSocketMessageBroker
class ChatWebSocketConfiguration(
    private val jwt: JwtTokenProvider,
    private val tokenBlacklist: TokenBlacklist,
    private val tracker: OnlineTracker,
    private val gate: WebSocketSubscriptionGate,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // 모바일 앱 전용이라 SockJS 폴백은 두지 않는다.
        registry.addEndpoint("/ws/chat").setAllowedOriginPatterns("*")
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    /**
     * 구독 인가 게이트를 **먼저** 건다. 인가에 실패한 SUBSCRIBE 는 예외로 끊겨서
     * 뒤따르는 인터셉터가 아예 돌지 않는다 — 남의 방을 구독하려던 요청이
     * `recordViewing` 으로 "보는 중"에 기록되는 일이 없어야 한다.
     *
     * 게이트가 chat 모듈이 아니라 `common` 에 있는 이유는 그쪽 KDoc 에 있다.
     */
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(gate, authenticationInterceptor())
    }

    /**
     * CONNECT 프레임에서 JWT 를 검증한다.
     *
     * 소켓은 한 번 열리면 계속 살아 있어서, 연결 시점에 막지 못하면 그 뒤로는 검사할 기회가 없다.
     * 토큰이 없거나 유효하지 않으면 연결 자체를 거부한다.
     */
    private fun authenticationInterceptor() = object : ChannelInterceptor {
        override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
            val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
                ?: return message

            if (accessor.command == StompCommand.SUBSCRIBE) {
                startViewing(accessor)
                return message
            }

            if (accessor.command == StompCommand.UNSUBSCRIBE) {
                stopViewing(accessor)
                return message
            }

            if (accessor.command != StompCommand.CONNECT) return message

            val token = accessor.getFirstNativeHeader("Authorization")
                ?.takeIf { it.startsWith("Bearer ") }
                ?.substring(7)
                ?: throw IllegalArgumentException("auth.unauthorized")

            if (tokenBlacklist.isBlacklisted(token)) throw IllegalArgumentException("auth.invalid-token")

            val claims = jwt.parseToClaims(token)
            if (jwt.extractTokenType(claims) != "access") throw IllegalArgumentException("auth.invalid-token")

            val id = jwt.extractId(claims)
            val role = jwt.extractRole(claims)

            // 이후 프레임에서 보낸 사람을 알아야 한다(전송 권한 검사 등).
            accessor.user = UsernamePasswordAuthenticationToken(id, null, listOf(SimpleGrantedAuthority(role)))

            return message
        }
    }

    /**
     * UNSUBSCRIBE 프레임은 구독 id 만 싣고 목적지를 안 준다. 그래서 SUBSCRIBE 때
     * 구독 id → 목적지를 세션 속성에 남겨둔다. 세션이 죽으면 같이 사라지니 따로 치울 게 없다.
     */
    private fun startViewing(accessor: StompHeaderAccessor) {
        val destination = accessor.destination ?: return
        // 게이트를 통과한 목적지엔 wave·notification 토픽도 섞여 있다. "보는 중"은 채팅방에만 있는 개념이다.
        if (!destination.startsWith(ROOM_TOPIC_PREFIX)) return

        val memberId = (accessor.user as? UsernamePasswordAuthenticationToken)?.principal as? Long ?: return
        accessor.subscriptionId?.let { accessor.sessionAttributes?.put("$VIEWING_ATTRIBUTE_PREFIX$it", destination) }

        tracker.recordViewing(memberId, destination)
    }

    private fun stopViewing(accessor: StompHeaderAccessor) {
        val memberId = (accessor.user as? UsernamePasswordAuthenticationToken)?.principal as? Long ?: return
        val subscriptionId = accessor.subscriptionId ?: return
        val destination = accessor.sessionAttributes
            ?.remove("$VIEWING_ATTRIBUTE_PREFIX$subscriptionId") as? String
            ?: return

        tracker.clearViewing(memberId, destination)
    }

    /**
     * 앱이 강제 종료되면 UNSUBSCRIBE 없이 소켓만 끊긴다. 인바운드 인터셉터는 그런 단절을
     * 프레임으로 보지 못해서, 정리는 세션 종료 이벤트에서 해야 한다.
     * 안 하면 그 회원이 영원히 "그 방을 보는 중"으로 남아 알림이 통째로 사라진다.
     *
     * 같은 회원이 기기 두 대로 붙어 있으면 한쪽만 끊겨도 전부 정리된다.
     * 남은 기기는 다음 구독 때 다시 기록되니, 세션별로 쪼개 관리하는 복잡도를 지지 않는다.
     */
    @Bean
    fun viewingCleanupListener(): ApplicationListener<SessionDisconnectEvent> = ApplicationListener { event ->
        ((event.user as? UsernamePasswordAuthenticationToken)?.principal as? Long)
            ?.let(tracker::clearAllViewing)
    }

    companion object {
        private const val ROOM_TOPIC_PREFIX = "/topic/chat/room/"
        private const val VIEWING_ATTRIBUTE_PREFIX = "viewing:"
    }
}
