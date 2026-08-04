package com.langlez.auth.api

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import java.time.Duration

/**
 * 토큰 쿠키 발급/삭제를 한 곳에서 만든다.
 *
 * OAuth2 로그인 성공, 토큰 재발급, 로그아웃이 각자 쿠키를 만들면
 * 속성(path/sameSite/httpOnly)이 하나라도 어긋나는 순간 브라우저가 다른 쿠키로 취급해
 * 덮어쓰기나 삭제가 안 먹는다.
 */
object AuthCookies {
    const val ACCESS_TOKEN = "accessToken"
    const val REFRESH_TOKEN = "refreshToken"

    fun issue(name: String, value: String, ttl: Duration): ResponseCookie =
        base(name, value).maxAge(ttl).build()

    /** maxAge=0 으로 브라우저에 삭제를 지시한다. 발급 때와 속성이 같아야 지워진다. */
    fun expire(name: String): ResponseCookie =
        base(name, "").maxAge(Duration.ZERO).build()

    fun HttpHeaders.setTokenCookies(accessToken: String, accessTtl: Duration, refreshToken: String, refreshTtl: Duration) {
        add(HttpHeaders.SET_COOKIE, issue(ACCESS_TOKEN, accessToken, accessTtl).toString())
        add(HttpHeaders.SET_COOKIE, issue(REFRESH_TOKEN, refreshToken, refreshTtl).toString())
    }

    fun HttpHeaders.expireTokenCookies() {
        add(HttpHeaders.SET_COOKIE, expire(ACCESS_TOKEN).toString())
        add(HttpHeaders.SET_COOKIE, expire(REFRESH_TOKEN).toString())
    }

    private fun base(name: String, value: String): ResponseCookie.ResponseCookieBuilder =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .sameSite("Lax")
}
