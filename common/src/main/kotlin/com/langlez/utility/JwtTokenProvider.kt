package com.langlez.utility

import com.langlez.exception.LanglezException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @param:Value($$"${jwt.secret}") private val secret: String,
    @param:Value($$"${jwt.access-token-ttl-secs}") private val accessTokenTTL: Long,
    @param:Value($$"${jwt.refresh-token-ttl-secs}") private val refreshTokenTTL: Long,
) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))
    }

    private val parser: JwtParser by lazy {
        Jwts.parser()
            .verifyWith(key)
            .build()
    }

    fun parseToClaims(token: String): Claims = try {
        parser.parseSignedClaims(token).payload
    } catch (e: Exception) {
        when (e) {
            is ExpiredJwtException ->
                throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.token-expired")

            is JwtException ->
                throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.invalid-token")

            else -> throw e
        }
    }

    fun createRefreshToken(id: Long, username: String, role: String): String {
        val now = Instant.now()
        val expiration = now.plus(Duration.ofSeconds(refreshTokenTTL))

        return Jwts.builder()
            .subject(id.toString())
            .claim("username", username)
            .claim("role", role)
            .claim("type", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key)
            .compact()
    }

    fun createAccessToken(id: Long, username: String, role: String): String {
        val now = Instant.now()
        val expiration = now.plus(Duration.ofSeconds(accessTokenTTL))

        return Jwts.builder()
            .subject(id.toString())
            .claim("username", username)
            .claim("role", role)
            .claim("type", "access")
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key)
            .compact()
    }

    fun extractId(claims: Claims): Long = claims.subject.toLong()
    fun extractId(token: String): Long = extractId(parseToClaims(token))

    fun extractRole(claims: Claims): String = claims.get("role", String::class.java)
    fun extractRole(token: String): String = extractRole(parseToClaims(token))

    fun extractUsername(claims: Claims): String = claims.get("username", String::class.java)
    fun extractUsername(token: String): String = extractUsername(parseToClaims(token))

    fun extractTokenType(claims: Claims): String = claims.get("type", String::class.java)
    fun extractTokenType(token: String): String = extractTokenType(parseToClaims(token))

    fun extractRemainingValiditySeconds(token: String): Long = extractRemainingValiditySeconds(parseToClaims(token))
    fun extractRemainingValiditySeconds(claims: Claims): Long {
        val expirationInstant = claims.expiration.toInstant()
        val now = Instant.now()
        return Duration.between(now, expirationInstant).seconds
    }

}
