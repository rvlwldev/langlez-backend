package com.langlez.matching.config

import com.langlez.core.LanglezException
import com.langlez.core.TokenBlacklist
import com.langlez.security.util.JwtParser
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import java.security.Principal

/**
 * module:chat의 ChatWebSocketConfiguration과 동일한 패턴의 독립 STOMP 엔드포인트.
 * `/ws/chat`을 재사용하지 않고 자체 `/ws/matching`을 두어 모듈 경계를 지킨다.
 * `/topic/matching/{memberId}` 구독은 반드시 본인 memberId에 대해서만 허용한다(IDOR 방지).
 */
@Configuration
@EnableWebSocketMessageBroker
class MatchingWebSocketConfiguration(
    private val jwtParser: JwtParser,
    private val tokenBlacklist: TokenBlacklist,
) : WebSocketMessageBrokerConfigurer {

    @Bean
    @Order(1)
    fun matchingWsSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher("/ws/matching/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        return http.build()
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/matching")
            .setAllowedOriginPatterns("*")
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(MatchingJwtChannelInterceptor(jwtParser, tokenBlacklist))
    }
}

class MatchingJwtChannelInterceptor(
    private val jwtParser: JwtParser,
    private val tokenBlacklist: TokenBlacklist,
) : ChannelInterceptor {

    private val destinationMemberIdPattern = Regex("^/?(?:app|topic)/matching/([^/]+)(?:/.*)?$")

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        when (accessor.command) {
            StompCommand.CONNECT -> authenticate(accessor)
            StompCommand.SUBSCRIBE -> authorizeOwnTopic(accessor)
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
            accessor.user = MatchingStompPrincipal(memberId)
        } catch (e: Exception) {
            throw LanglezException(401, "auth.invalid-token")
        }
    }

    private fun authorizeOwnTopic(accessor: StompHeaderAccessor) {
        val destination = accessor.destination ?: return
        val targetMemberId = destinationMemberIdPattern.find(destination)?.groupValues?.get(1) ?: return

        val memberId = (accessor.user as? MatchingStompPrincipal)?.memberId
            ?: throw LanglezException(401, "auth.invalid-token")

        if (targetMemberId != memberId.toString()) {
            throw LanglezException(403, "matching.topic-forbidden")
        }
    }
}

class MatchingStompPrincipal(val memberId: Long) : Principal {
    override fun getName(): String = memberId.toString()
}
