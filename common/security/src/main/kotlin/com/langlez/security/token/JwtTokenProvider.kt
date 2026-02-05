package com.langlez.security.token

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @param:Value("\${jwt.secret}") private val secretKeyString: String,
    @param:Value("\${jwt.access-token-validity-in-seconds:3600}") private val accessTokenValidityInSeconds: Long,
    @param:Value("\${jwt.refresh-token-validity-in-seconds:1209600}") private val refreshTokenValidityInSeconds: Long,
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKeyString))
    }

    fun createAccessToken(email: String, role: String): String {
        val claims = Jwts.claims().subject(email).add("role", role).build()
        val now = Date()
        val validity = Date(now.time + accessTokenValidityInSeconds * 1000)

        return Jwts.builder()
            .claims(claims)
            .issuedAt(now)
            .expiration(validity)
            .signWith(key)
            .compact()
    }

    fun createRefreshToken(email: String): String {
        val now = Date()
        val validity = Date(now.time + refreshTokenValidityInSeconds * 1000)

        return Jwts.builder()
            .subject(email)
            .issuedAt(now)
            .expiration(validity)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Boolean =
        try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
            true
        } catch (e: Exception) {
            false
        }

    fun getEmail(token: String): String =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject

    fun getRole(token: String): String =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
            .get("role", String::class.java) ?: "ROLE_MEMBER"
}