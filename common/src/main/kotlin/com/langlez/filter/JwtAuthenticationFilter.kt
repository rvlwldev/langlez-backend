package com.langlez.filter

import com.langlez.core.TokenBlacklist
import com.langlez.exception.LanglezException
import com.langlez.utility.JwtTokenProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpStatus
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
    @param:Lazy @param:Qualifier("handlerExceptionResolver") private val resolver: HandlerExceptionResolver
) : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        try {
            val token = req.getHeader("Authorization")
                ?.takeIf { it.startsWith("Bearer ") }
                ?.substring(7)
                ?: return chain.doFilter(req, res)

            if (tokenBlacklist.isBlacklisted(token)) {
                throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.invalid-token")
            }

            val claims = jwt.parseToClaims(token)

            if (jwt.extractTokenType(claims) != "access")
                throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.invalid-token")

            val id = jwt.extractId(claims)
            val role = jwt.extractRole(claims)
            val authentication = UsernamePasswordAuthenticationToken(id, null, listOf(SimpleGrantedAuthority(role)))
            SecurityContextHolder.getContext().authentication = authentication

            chain.doFilter(req, res)
        } catch (e: Exception) {
            resolver.resolveException(req, res, null, e)
        }
    }

}
