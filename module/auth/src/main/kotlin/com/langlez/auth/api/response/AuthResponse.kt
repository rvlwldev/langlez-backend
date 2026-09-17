package com.langlez.auth.api.response


class AuthResponse {
    data class NewTokens(val refreshToken: String, val accessToken: String)
}