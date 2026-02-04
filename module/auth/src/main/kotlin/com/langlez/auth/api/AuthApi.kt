package com.langlez.auth.api

import com.langlez.auth.application.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthApi(
    private val authService: AuthService,
) {
    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshTokenRequest): ResponseEntity<TokenResponse> {
        val response = authService.refresh(request.refreshToken)
        return ResponseEntity.ok(response)
    }
}

data class RefreshTokenRequest(
    val refreshToken: String,
)
