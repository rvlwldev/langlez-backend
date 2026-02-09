package com.langlez.auth.api

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token cannot be blank")
    val refreshToken: String,
)