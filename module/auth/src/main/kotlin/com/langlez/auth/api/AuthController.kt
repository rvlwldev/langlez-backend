package com.langlez.auth.api

import com.langlez.auth.api.AuthRequest.RefreshToken
import com.langlez.auth.api.AuthResponse.NewTokens
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
    fun refresh(@RequestBody @Valid request: RefreshToken): NewTokens? =
        service.refresh(request.refreshToken)
            .let { (refresh, access) -> NewTokens(refresh, access) }

}
