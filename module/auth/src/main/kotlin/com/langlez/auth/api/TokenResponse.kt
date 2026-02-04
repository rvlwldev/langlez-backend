package com.langlez.auth.api

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
)
