package com.langlez.auth.api


class AuthResponse {
    data class NewTokens(val accessToken: String, val refreshToken: String)
}