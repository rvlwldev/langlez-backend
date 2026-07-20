package com.langlez.auth.oauth2

import com.langlez.security.util.JwtParser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class OAuth2SuccessHandler(
    private val jwt: JwtParser,
    private val redisson: RedissonClient,
    @param:Value($$"${app.oauth2.redirect-uri}") private val uri: String,
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(req: HttpServletRequest, res: HttpServletResponse, auth: Authentication) {
        val user = auth.principal as OAuth2LanglezUser
        val refreshToken = jwt.createRefreshToken(user.id, user.role)
        val accessToken = jwt.createAccessToken(user.id, user.role)

        val accessCookie = ResponseCookie.from("accessToken", accessToken)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .sameSite("Lax")
            .build()

        val refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .sameSite("Lax")
            .build()

        res.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString())
        res.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString())

        redisson.getBucket<String>("refresh_token:${user.id}").set(refreshToken, 14, TimeUnit.DAYS)
        clearAuthenticationAttributes(req)
        redirectStrategy.sendRedirect(req, res, uri)
    }

}