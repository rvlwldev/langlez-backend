package com.langlez.auth.api

import com.langlez.annotation.MemberId
import com.langlez.auth.api.AuthRequest.RefreshToken
import com.langlez.auth.api.AuthResponse.NewTokens
import com.langlez.auth.application.AuthService
import com.langlez.exception.LanglezException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val service: AuthService) {

    @PostMapping("/refresh")
    fun refresh(@RequestBody @Valid request: RefreshToken): NewTokens =
        service.refresh(request.refreshToken)
            .let { (refresh, access) -> NewTokens(refresh, access) }

    @PostMapping("/logout")
    @ResponseStatus(NO_CONTENT)
    fun logout(@MemberId memberId: Long, @RequestHeader("Authorization") authHeader: String) {
        val accessToken = authHeader.takeIf { it.startsWith("Bearer ") }?.substring(7)
            ?: throw LanglezException(400, "auth.invalid-request")
        service.logout(memberId, accessToken)
    }
}
