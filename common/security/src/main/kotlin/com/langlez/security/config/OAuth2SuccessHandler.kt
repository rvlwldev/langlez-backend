package com.langlez.security.config

import com.langlez.security.token.JwtTokenProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.util.concurrent.TimeUnit

@Component
class OAuth2SuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
    private val redisTemplate: StringRedisTemplate,
    @Value("\${app.oauth2.redirect-uri:http://localhost:3000/oauth2/redirect}") private val redirectUri: String,
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oAuth2User = authentication.principal as OAuth2User
        val email = oAuth2User.attributes["email"] as String
        val role = authentication.authorities.firstOrNull { it.authority.startsWith("ROLE_") }?.authority
            ?: "ROLE_MEMBER"

        val accessToken = jwtTokenProvider.createAccessToken(email, role)
        val refreshToken = jwtTokenProvider.createRefreshToken(email)

        redisTemplate.opsForValue().set(
            "refresh_token:$email",
            refreshToken,
            14,
            TimeUnit.DAYS
        )

        val targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
            .queryParam("accessToken", accessToken)
            .queryParam("refreshToken", refreshToken)
            .build().toUriString()

        clearAuthenticationAttributes(request)
        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}
