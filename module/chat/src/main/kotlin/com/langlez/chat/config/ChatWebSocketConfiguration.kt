package com.langlez.chat.config

import com.langlez.core.LanglezException
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
    private val jwtParser: JwtParser
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
        registration.interceptors(JwtChannelInterceptor(jwtParser))
    }
}

class JwtChannelInterceptor(
    private val jwtParser: JwtParser
) : ChannelInterceptor {
    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
        if (accessor != null && StompCommand.CONNECT == accessor.command) {
            val authHeader = accessor.getFirstNativeHeader("Authorization")
            val token = authHeader?.takeIf { it.startsWith("Bearer ") }?.substring(7)
                ?: throw LanglezException(401, "auth.invalid-token")

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
        return message
    }
}

class StompPrincipal(val memberId: Long) : Principal {
    override fun getName(): String = memberId.toString()
}
