package com.langlez.auth.api

import com.langlez.auth.application.AuthService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val service: AuthService) {

    @PostMapping("/refresh")
    suspend fun refresh(@RequestBody @Valid request: RefreshTokenRequest): TokenResponse? =
            service.refresh(request.refreshToken)

}
