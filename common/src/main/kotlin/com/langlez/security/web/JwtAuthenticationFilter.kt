package com.langlez.security.web

import com.langlez.core.LanglezException
import com.langlez.core.TokenBlacklist
import com.langlez.security.event.MemberAuthenticatedEvent
import com.langlez.security.util.JwtTokenProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

@Component
class JwtAuthenticationFilter(
    private val jwt: JwtTokenProvider,
    private val tokenBlacklist: TokenBlacklist,
    private val eventPublisher: ApplicationEventPublisher,
    @param:Lazy @param:Qualifier("handlerExceptionResolver") private val resolver: HandlerExceptionResolver
) : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        try {
            val token = req.getHeader("Authorization")
                ?.takeIf { it.startsWith("Bearer ") }
                ?.substring(7)
                ?: return chain.doFilter(req, res)

            if (tokenBlacklist.isBlacklisted(token)) {
                throw LanglezException(401, "auth.invalid-token")
            }

            val claims = jwt.parseClaims(token)

            if (jwt.extractTokenType(claims) != "access")
                throw LanglezException(401, "auth.invalid-token")

            val id = jwt.extractId(claims)
            val role = jwt.extractRole(claims)
            val authentication = UsernamePasswordAuthenticationToken(id, null, listOf(SimpleGrantedAuthority(role)))
            SecurityContextHolder.getContext().authentication = authentication

            eventPublisher.publishEvent(MemberAuthenticatedEvent(id))

            chain.doFilter(req, res)
        } catch (e: Exception) {
            resolver.resolveException(req, res, null, e)
        }
    }

}
