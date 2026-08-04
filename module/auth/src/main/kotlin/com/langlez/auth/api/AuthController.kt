package com.langlez.auth.api

import com.langlez.annotation.MemberId
import com.langlez.auth.api.AuthCookies.expireTokenCookies
import com.langlez.auth.api.AuthCookies.setTokenCookies
import com.langlez.auth.api.AuthRequest.RefreshToken
import com.langlez.auth.api.AuthResponse.NewTokens
import com.langlez.auth.application.AuthService
import com.langlez.exception.LanglezException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val service: AuthService) {

    /**
     * 리프레시 토큰은 쿠키(웹) 또는 바디(앱)로 받는다.
     *
     * OAuth2 로그인이 토큰을 HttpOnly 쿠키로 내려주기 때문에 웹 클라이언트의 JS 는
     * 그 값을 읽어 바디에 실을 수 없다. 쿠키를 우선 보고, 없으면 바디를 쓴다.
     */
    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(AuthCookies.REFRESH_TOKEN, required = false) cookieToken: String?,
        @RequestBody(required = false) request: RefreshToken?,
    ): ResponseEntity<NewTokens> {
        val token = cookieToken?.takeIf { it.isNotBlank() }
            ?: request?.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw LanglezException(400, "auth.invalid-request")

        val (refresh, access) = service.refresh(token)

        val headers = HttpHeaders().apply {
            setTokenCookies(access, service.accessTokenTtl, refresh, service.refreshTokenTtl)
        }

        return ResponseEntity.ok().headers(headers).body(NewTokens(refresh, access))
    }

    @PostMapping("/logout")
    fun logout(
        @MemberId memberId: Long,
        @RequestHeader("Authorization") authHeader: String,
    ): ResponseEntity<Void> {
        val accessToken = authHeader.takeIf { it.startsWith("Bearer ") }?.substring(7)
            ?: throw LanglezException(400, "auth.invalid-request")
        service.logout(memberId, accessToken)

        val headers = HttpHeaders().apply { expireTokenCookies() }

        return ResponseEntity.status(NO_CONTENT).headers(headers).build()
    }
}
