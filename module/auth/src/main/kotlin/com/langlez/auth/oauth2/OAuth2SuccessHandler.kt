package com.langlez.auth.oauth2

import com.langlez.security.util.JwtParser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.util.concurrent.TimeUnit

@Component
class OAuth2SuccessHandler(
    private val jwt: JwtParser,
    private val redis: StringRedisTemplate,
    @param:Value($$"${app.oauth2.redirect-uri}") private val uri: String,
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(req: HttpServletRequest, res: HttpServletResponse, auth: Authentication) {
        val user = auth.principal as OAuth2LanglezUser
        val refreshToken = jwt.createRefreshToken(user.id, user.role)
        val accessToken = jwt.createAccessToken(user.id, user.role)
        val uriComponent = UriComponentsBuilder.fromUriString(uri)
            .queryParam("refreshToken", refreshToken)
            .queryParam("accessToken", accessToken)
            .build()
            .toUriString()

        redis.opsForValue().set("refresh_token:${user.id}", refreshToken, 14, TimeUnit.DAYS)
        clearAuthenticationAttributes(req)
        redirectStrategy.sendRedirect(req, res, uriComponent)
    }

}