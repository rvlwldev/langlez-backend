package com.langlez.auth.application

import com.langlez.auth.api.TokenResponse
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val tokenProvider: TokenProvider,
) {
    fun refresh(refreshToken: String): TokenResponse {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw IllegalArgumentException("Invalid refresh token")
        }

        val email = tokenProvider.getEmail(refreshToken)

        val newAccessToken = tokenProvider.createAccessToken(email, "ROLE_MEMBER")
        val newRefreshToken = tokenProvider.createRefreshToken(email)

        return TokenResponse(newAccessToken, newRefreshToken)
    }
}
