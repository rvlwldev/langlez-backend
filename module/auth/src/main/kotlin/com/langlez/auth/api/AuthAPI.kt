package com.langlez.auth.api

import com.langlez.auth.api.request.AuthRequest.RefreshToken
import com.langlez.auth.api.response.AuthResponse.NewTokens
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest

/**
 * 모바일 앱 전용이라 쿠키를 쓰지 않는다. 토큰은 응답 바디로만 내려가고,
 * 요청은 `Authorization` 헤더(access) / 바디(refresh)로만 받는다.
 */
@Tag(name = "Auth", description = "로그인 세션(토큰) 관리 API")
interface AuthAPI {

    @Operation(
        summary = "토큰 갱신",
        description = "리프레시 토큰으로 액세스·리프레시 토큰 쌍을 새로 발급한다. 1인 1기기 정책상 " +
            "X-Device-Id 헤더가 필수다.",
    )
    fun refresh(
        request: RefreshToken,
        servletRequest: HttpServletRequest,
        @Parameter(description = "기기 식별자") deviceId: String?,
    ): NewTokens

    @Operation(summary = "로그아웃", description = "리프레시 토큰과 기기 바인딩을 지우고 액세스 토큰을 무효화한다.")
    fun logout(memberId: Long, authHeader: String)
}
