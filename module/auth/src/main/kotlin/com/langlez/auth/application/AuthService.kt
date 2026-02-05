package com.langlez.auth.application

import com.langlez.auth.api.TokenResponse
import com.langlez.member.application.MemberUseCase
import com.langlez.security.token.JwtTokenProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class AuthService(
    private val tokenProvider: JwtTokenProvider,
    private val memberUseCase: MemberUseCase,
    private val redisTemplate: StringRedisTemplate,
) {
    fun refresh(refreshToken: String): TokenResponse {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw IllegalArgumentException("Invalid refresh token")
        }

        val email = tokenProvider.getEmail(refreshToken)
        val savedToken = redisTemplate.opsForValue().get("refresh_token:$email")

        if (savedToken != refreshToken) {
            throw IllegalArgumentException("Refresh token mismatch or expired")
        }

        val member = memberUseCase.getMember(email)
        val newAccessToken = tokenProvider.createAccessToken(email, "ROLE_${member.role.name}")
        val newRefreshToken = tokenProvider.createRefreshToken(email)

        redisTemplate.opsForValue().set(
            "refresh_token:$email",
            newRefreshToken,
            14,
            TimeUnit.DAYS
        )

        return TokenResponse(newAccessToken, newRefreshToken)
    }
}

