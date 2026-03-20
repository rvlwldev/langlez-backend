package com.langlez.security.web

import com.langlez.exception.LanglezException
import com.langlez.security.util.JwtParser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

@Component
class JwtAuthenticationFilter(
    private val jwt: JwtParser,
    @Lazy @Qualifier("handlerExceptionResolver") private val resolver: HandlerExceptionResolver
) : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        try {
            val token = req.getHeader("Authorization")
                ?.takeIf { it.startsWith("Bearer ") }
                ?.substring(7)
                ?: return chain.doFilter(req, res)

            val claims = jwt.parseClaims(token)

            if (claims.get("type", String::class.java) != "access")
                throw LanglezException(UNAUTHORIZED, "auth.invalid-token")

            val id = claims.subject.toLong()
            val role = claims.get("role", String::class.java)
            val authentication = UsernamePasswordAuthenticationToken(id, null, listOf(SimpleGrantedAuthority(role)))
            SecurityContextHolder.getContext().authentication = authentication

            chain.doFilter(req, res)
        } catch (e: Exception) {
            resolver.resolveException(req, res, null, e)
        }
    }

}
