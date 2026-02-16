package com.langlez.auth.application

import com.langlez.auth.api.TokenResponse
import com.langlez.common.exception.LanglezException
import com.langlez.member.application.MemberService
import com.langlez.security.token.JwtTokenProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val tokenProvider: JwtTokenProvider,
    private val memberService: MemberService,
    private val redisTemplate: StringRedisTemplate,
) {
    suspend fun refresh(refreshToken: String): TokenResponse {
        // JWT 파싱하여 email 추출 (Redis key 생성을 위해 필수)
        val email = try {
            tokenProvider.getEmail(refreshToken)
        } catch (e: Exception) {
            throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.invalid-token", e)
        }

        // Redis에서 저장된 토큰 조회 및 비교
        val savedToken = redisTemplate.opsForValue().get("refresh_token:$email")
        if (savedToken != refreshToken) {
            throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.token-mismatch", null)
        }

        // JWT 서명 검증
        if (!tokenProvider.validateToken(refreshToken)) {
            // 유효하지 않은 토큰 발견 시 Redis에서도 삭제
            redisTemplate.delete("refresh_token:$email")
            throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.invalid-token", null)
        }

        val member = memberService.getMember(email)
        val newAccessToken = tokenProvider.createAccessToken(email, "ROLE_${member.role.name}")
        val newRefreshToken = tokenProvider.createRefreshToken(email)

        // Token Rotation: 기존 토큰 삭제 후 새 토큰 저장
        redisTemplate.delete("refresh_token:$email")
        redisTemplate.opsForValue().set("refresh_token:$email", newRefreshToken, 14, TimeUnit.DAYS)

        return TokenResponse(newAccessToken, newRefreshToken)
    }
}
