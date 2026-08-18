package com.langlez.auth.api

import com.langlez.annotation.MemberId
import com.langlez.auth.api.AuthRequest.RefreshToken
import com.langlez.auth.api.AuthResponse.NewTokens
import com.langlez.auth.application.AccessContext
import com.langlez.auth.application.AuthService
import com.langlez.exception.LanglezException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 모바일 앱 전용이라 쿠키를 쓰지 않는다. 토큰은 응답 바디로만 내려가고,
 * 요청은 `Authorization` 헤더(access) / 바디(refresh)로만 받는다.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val service: AuthService) {

    /**
     * 기기 id 는 필수다. 선택으로 두면 헤더를 빼는 것만으로 1인 1기기 검증을 우회할 수 있고,
     * 바인딩이 영영 생성되지 않아 정책 자체가 무력화된다.
     */
    @PostMapping("/refresh")
    fun refresh(
        @RequestBody @Valid request: RefreshToken,
        servletRequest: HttpServletRequest,
        @RequestHeader(DEVICE_ID_HEADER, required = false) deviceId: String?,
    ): NewTokens {
        if (deviceId.isNullOrBlank()) throw LanglezException(400, "auth.device-id-required")

        val (refresh, access) = service.refresh(request.refreshToken, accessContext(servletRequest, deviceId))
        return NewTokens(refresh, access)
    }

    @PostMapping("/logout")
    @ResponseStatus(NO_CONTENT)
    fun logout(@MemberId memberId: Long, @RequestHeader("Authorization") authHeader: String) {
        val accessToken = authHeader.takeIf { it.startsWith("Bearer ") }?.substring(7)
            ?: throw LanglezException(400, "auth.invalid-request")

        service.logout(memberId, accessToken)
    }

    companion object {
        const val DEVICE_ID_HEADER = "X-Device-Id"

        /** 프록시 뒤에 있으면 remoteAddr 이 프록시 주소라 X-Forwarded-For 의 첫 값을 우선한다. */
        fun accessContext(request: HttpServletRequest, deviceId: String?): AccessContext {
            val ip = request.getHeader("X-Forwarded-For")
                ?.split(",")
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: request.remoteAddr

            return AccessContext(ip, deviceId?.takeIf { it.isNotBlank() })
        }
    }
}
