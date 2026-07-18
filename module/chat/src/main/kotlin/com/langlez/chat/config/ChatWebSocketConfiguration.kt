package com.langlez.chat.config

import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.core.LanglezException
import com.langlez.core.TokenBlacklist
import com.langlez.security.util.JwtParser
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import java.security.Principal

@Configuration
@EnableWebSocketMessageBroker
class ChatWebSocketConfiguration(
    private val jwtParser: JwtParser,
    private val tokenBlacklist: TokenBlacklist,
    private val chatRoomRepository: ChatRoomRepository,
) : WebSocketMessageBrokerConfigurer {

    @Bean
    @Order(1)
    fun wsSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher("/ws/chat/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        return http.build()
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/chat")
            .setAllowedOriginPatterns("*")
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(JwtChannelInterceptor(jwtParser, tokenBlacklist, chatRoomRepository))
    }
}

/**
 * CONNECT 시 JWT를 검증해 Principal을 세션에 심고,
 * SUBSCRIBE/SEND가 특정 채팅방(`/topic/chat/room/{roomId}...`, `/app/chat/{roomId}/...`)을
 * 대상으로 할 때는 그 방의 참여자인지 확인한다 (IDOR 방지 — 룸 ID를 안다고 아무나 구독/발신하지 못하게).
 */
class JwtChannelInterceptor(
    private val jwtParser: JwtParser,
    private val tokenBlacklist: TokenBlacklist,
    private val chatRoomRepository: ChatRoomRepository,
) : ChannelInterceptor {

    private val roomIdPattern = Regex("^/?(?:app|topic)/chat/(?:room/)?([^/]+)(?:/.*)?$")

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        when (accessor.command) {
            StompCommand.CONNECT -> authenticate(accessor)
            StompCommand.SUBSCRIBE, StompCommand.SEND -> authorizeRoomAccess(accessor)
            else -> {}
        }
        return message
    }

    private fun authenticate(accessor: StompHeaderAccessor) {
        val authHeader = accessor.getFirstNativeHeader("Authorization")
        val token = authHeader?.takeIf { it.startsWith("Bearer ") }?.substring(7)
            ?: throw LanglezException(401, "auth.invalid-token")

        if (tokenBlacklist.isBlacklisted(token)) {
            throw LanglezException(401, "auth.invalid-token")
        }

        try {
            val memberId = jwtParser.extractID(token)
            if (jwtParser.extractTokenType(token) != "access") {
                throw LanglezException(401, "auth.invalid-token")
            }
            accessor.user = StompPrincipal(memberId)
        } catch (e: Exception) {
            throw LanglezException(401, "auth.invalid-token")
        }
    }

    private fun authorizeRoomAccess(accessor: StompHeaderAccessor) {
        val destination = accessor.destination ?: return
        val roomId = roomIdPattern.find(destination)?.groupValues?.get(1) ?: return

        val memberId = (accessor.user as? StompPrincipal)?.memberId
            ?: throw LanglezException(401, "auth.invalid-token")

        val room = chatRoomRepository.findById(roomId)
            ?: throw LanglezException(404, "chat.room-not-found")

        if (!room.hasParticipant(memberId)) {
            throw LanglezException(403, "chat.room-forbidden")
        }
    }
}

class StompPrincipal(val memberId: Long) : Principal {
    override fun getName(): String = memberId.toString()
}
