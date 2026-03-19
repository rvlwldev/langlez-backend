package com.langlez.auth.api

import jakarta.validation.constraints.NotBlank

class AuthRequest {
    data class RefreshToken(@field:NotBlank(message = "Refresh token cannot be blank") val refreshToken: String)
}