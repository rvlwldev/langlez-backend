package com.langlez.auth.api

import com.langlez.auth.application.AuthService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/refresh")
    fun refresh(@RequestBody @Valid request: RefreshTokenRequest): ResponseEntity<TokenResponse> {
        val response = authService.refresh(request.refreshToken)
        return ResponseEntity.ok(response)
    }
}

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token cannot be blank")
    val refreshToken: String,
)
