package com.langlez.auth.application

interface TokenProvider {
    fun createAccessToken(email: String, role: String): String
    fun createRefreshToken(email: String): String
    fun validateToken(token: String): Boolean
    fun getEmail(token: String): String
}
