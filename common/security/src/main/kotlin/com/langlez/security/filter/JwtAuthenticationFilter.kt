package com.langlez.security.filter

import com.langlez.security.token.JwtTokenProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(private val jwtTokenProvider: JwtTokenProvider) : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val token = resolveToken(req)

        if (token != null && jwtTokenProvider.validateToken(token)) {
            val email = jwtTokenProvider.getEmail(token)
            val role = jwtTokenProvider.getRole(token)
            val authorities = listOf(SimpleGrantedAuthority(role))

            val authentication = UsernamePasswordAuthenticationToken(email, null, authorities)
            SecurityContextHolder.getContext().authentication = authentication
        }

        chain.doFilter(req, res)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")

        if (bearerToken != null && bearerToken.startsWith("Bearer "))
            return bearerToken.substring(7)

        return null
    }
}
