package com.langlez.security.token

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor("dummy-secret-key-must-be-very-long-and-secure-enough-for-hs256".toByteArray())
    private val validityInMilliseconds: Long = 3600000

    fun createToken(payload: String): String {
        val now = Date()
        val validity = Date(now.time + validityInMilliseconds)

        return Jwts
            .builder()
            .subject(payload)
            .issuedAt(now)
            .expiration(validity)
            .signWith(secretKey)
            .compact()
    }

    fun validateToken(token: String): Boolean =
        try {
            Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
            true
        } catch (e: Exception) {
            false
        }

    fun getPayload(token: String): String =
        Jwts
            .parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject
}
