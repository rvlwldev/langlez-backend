package com.langlez.wave.config

import com.langlez.core.LanglezException
import com.langlez.core.TokenBlacklist
import com.langlez.member.domain.MemberRole
import com.langlez.security.util.JwtParser
import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.domain.WaveRoomRepository
import com.langlez.wave.infrastructure.WaveViewerTracker
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Component
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.security.Principal

@Configuration
@EnableWebSocketMessageBroker
class WaveWebSocketConfiguration(
    private val jwtParser: JwtParser,
    private val tokenBlacklist: TokenBlacklist,
    private val waveRoomRepository: WaveRoomRepository,
    private val viewerTracker: WaveViewerTracker,
) : WebSocketMessageBrokerConfigurer {

    @Bean
    @Order(1)
    fun waveWsSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher("/ws/wave/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        return http.build()
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/wave")
            .setAllowedOriginPatterns("*")
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(WaveJwtChannelInterceptor(jwtParser, tokenBlacklist, waveRoomRepository, viewerTracker))
    }
}

/**
 * CONNECT 시 JWT를 검증해 Principal을 세션에 심고,
 * SUBSCRIBE/SEND가 특정 Wave 방(`/topic/wave/{roomId}/...`, `/app/wave/{roomId}/...`)을 대상으로 할 때는
 * 그 방이 실제로 존재하고 아직 종료되지 않았는지 확인한다 (IDOR 방지 — 존재하지 않거나 이미 종료된 방을 구독/발신하지 못하게).
 * Wave 방은 누구나 시청 가능하므로 chat과 달리 참가자 제한은 없다.
 */
class WaveJwtChannelInterceptor(
    private val jwtParser: JwtParser,
    private val tokenBlacklist: TokenBlacklist,
    private val waveRoomRepository: WaveRoomRepository,
    private val viewerTracker: WaveViewerTracker,
) : ChannelInterceptor {

    private val roomIdPattern = Regex("^/?(?:app|topic)/wave/([^/]+)(?:/.*)?$")

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        when (accessor.command) {
            StompCommand.CONNECT -> authenticate(accessor)
            StompCommand.SUBSCRIBE -> {
                val room = authorizeRoomAccess(accessor)
                if (room != null) {
                    val memberId = (accessor.user as? WaveStompPrincipal)?.memberId
                    if (memberId != null) {
                        checkBanAndParticipantLimit(room, memberId)
                    }
                    trackJoin(accessor, room.id)
                }
            }
            StompCommand.SEND -> {
                val room = authorizeRoomAccess(accessor)
                if (room != null) {
                    val memberId = (accessor.user as? WaveStompPrincipal)?.memberId
                    if (memberId != null && viewerTracker.isBanned(room.id, memberId)) {
                        throw LanglezException(403, "wave.banned-user")
                    }
                }
            }
            StompCommand.UNSUBSCRIBE -> trackLeave(accessor)
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
            val memberId = jwtParser.extractId(token)
            if (jwtParser.extractTokenType(token) != "access") {
                throw LanglezException(401, "auth.invalid-token")
            }
            val roleStr = try { jwtParser.extractRole(token) } catch (e: Exception) { null }
            val role = roleStr?.let {
                try { MemberRole.valueOf(it) } catch (e: Exception) { null }
            } ?: MemberRole.MEMBER
            accessor.user = WaveStompPrincipal(memberId, role)
        } catch (e: Exception) {
            throw LanglezException(401, "auth.invalid-token")
        }
    }

    private fun authorizeRoomAccess(accessor: StompHeaderAccessor): WaveRoom? {
        val destination = accessor.destination ?: return null
        val roomId = roomIdPattern.find(destination)?.groupValues?.get(1)?.toLongOrNull() ?: return null

        val room = waveRoomRepository.findById(roomId)
            ?: throw LanglezException(404, "wave.room-not-found")

        if (room.isEnded()) {
            throw LanglezException(403, "wave.room-ended")
        }

        return room
    }

    private fun checkBanAndParticipantLimit(room: WaveRoom, memberId: Long) {
        if (viewerTracker.isBanned(room.id, memberId)) {
            throw LanglezException(403, "wave.banned-user")
        }

        if (!viewerTracker.addViewerIfAllowed(room.id, memberId, room.maxParticipants)) {
            throw LanglezException(403, "wave.room-full")
        }
    }

    private fun trackJoin(accessor: StompHeaderAccessor, roomId: Long) {
        val memberId = (accessor.user as? WaveStompPrincipal)?.memberId ?: return
        val sessionId = accessor.sessionId ?: return
        viewerTracker.trackSession(sessionId, roomId, memberId)
    }

    private fun trackLeave(accessor: StompHeaderAccessor) {
        val sessionId = accessor.sessionId ?: return
        viewerTracker.leave(sessionId)
    }
}

/** 클라이언트가 세션을 끊었을 때(UNSUBSCRIBE 없이 바로 연결 종료) 시청자 집계에서 제거한다. */
@Component
class WaveSessionDisconnectListener(
    private val viewerTracker: WaveViewerTracker,
) {
    @EventListener
    fun handleSessionDisconnect(event: SessionDisconnectEvent) {
        viewerTracker.leave(event.sessionId)
    }
}

class WaveStompPrincipal(
    val memberId: Long,
    val role: MemberRole = MemberRole.MEMBER,
) : Principal {
    override fun getName(): String = memberId.toString()
}
