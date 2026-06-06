package com.langlez.auth.api


class AuthResponse {
    data class NewTokens(val refreshToken: String, val accessToken: String)
}