package com.langlez.filter

import com.langlez.core.TokenBlacklist
import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberReader
import com.langlez.member.contract.MemberReader.Status.ACTIVE
import com.langlez.member.contract.MemberReader.Status.CREATED
import com.langlez.member.contract.MemberReader.Status.SUSPENDED
import com.langlez.member.contract.MemberReader.Status.WITHDRAWN
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
    private val members: MemberReader,
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

        // 인증을 심기 전에 검사한다. 뒤에 두면 거부된 요청도 컨텍스트에 인증이 남는다.
        requireUsableAccount(id, req.requestURI)

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(id, null, listOf(SimpleGrantedAuthority(role)))
    }

    /**
     * 정지·탈퇴 회원의 남은 액세스 토큰을 즉시 막는다.
     *
     * 상태 검사가 로그인·토큰 갱신에만 있으면 계정을 정지시켜도 이미 발급된 액세스 토큰이
     * 만료될 때까지 모든 API 가 그대로 통과한다. 그 구멍을 매 요청 경로에서 닫는다.
     *
     * `CREATED` 는 막지 않는다. 가입 직후 상태가 `CREATED` 이고 이를 `ACTIVE` 로 올리는
     * `Member.verify()` 를 호출하는 엔드포인트가 아직 없다. 여기서 막으면 신규 가입자가 전부 잠긴다.
     */
    private fun requireUsableAccount(id: Long, uri: String) {
        // 정지·탈퇴 회원도 로그아웃은 할 수 있어야 리프레시 토큰과 기기 바인딩이 정리된다.
        // 이 접두사 아래의 다른 엔드포인트인 토큰 갱신은 AuthService 가 스스로 상태를 확인하므로
        // 여기서 빼도 정지 회원이 세션을 연장할 구멍은 생기지 않는다.
        if (uri.startsWith(AUTH_PATH_PREFIX)) return

        when (members.findStatus(id)) {
            CREATED, ACTIVE -> Unit
            SUSPENDED -> throw LanglezException(HttpStatus.FORBIDDEN, "member.suspended")
            WITHDRAWN -> throw LanglezException(HttpStatus.FORBIDDEN, "member.withdrawn")
            // 회원 행은 탈퇴해도 남는다. 없다는 건 실재하지 않는 id 를 담은 토큰이라는 뜻이라
            // 갱신 경로(AuthService.refresh)와 같이 401 로 다시 로그인시킨다.
            null -> throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.invalid-token")
        }
    }

    companion object {
        private const val AUTH_PATH_PREFIX = "/api/v1/auth/"
    }

}
