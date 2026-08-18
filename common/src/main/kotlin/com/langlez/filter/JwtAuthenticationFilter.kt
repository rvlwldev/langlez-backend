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
        // 인증 수립까지만 감싼다. chain.doFilter 를 함께 감싸면 하류의 AccessDeniedException 을
        // 여기서 가로채, ExceptionTranslationFilter 의 accessDeniedHandler 가 영영 실행되지 않는다.
        try {
            authenticate(req)
        } catch (e: Exception) {
            resolver.resolveException(req, res, null, e)
            return
        }

        // 컨텍스트는 여기서 비우지 않는다. SecurityContextHolderFilter 가 이미 체인 전체를
        // finally 로 감싸 정리한다. 여기서 비우면 예외가 올라가는 도중 익명 토큰이 사라져
        // ExceptionTranslationFilter 가 미인증 요청에 401 대신 403 을 준다.
        chain.doFilter(req, res)
    }

    private fun authenticate(req: HttpServletRequest) {
        val token = req.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.substring(7)
            ?: return

        if (tokenBlacklist.isBlacklisted(token)) {
            throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.invalid-token")
        }

        val claims = jwt.parseToClaims(token)

        if (jwt.extractTokenType(claims) != "access")
            throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.invalid-token")

        val id = jwt.extractId(claims)
        val role = jwt.extractRole(claims)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(id, null, listOf(SimpleGrantedAuthority(role)))
    }

}
