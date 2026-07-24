package com.langlez.security.util

import com.langlez.core.LanglezException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
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

    private val key: SecretKey by lazy { Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)) }

    private val parser: io.jsonwebtoken.JwtParser by lazy {
        Jwts.parser()
            .verifyWith(key)
            .build()
    }

    fun parseClaims(token: String): Claims = try {
        parser.parseSignedClaims(token).payload
    } catch (e: Exception) {
        when (e) {
            is ExpiredJwtException -> throw LanglezException(401, "auth.token-expired")
            is JwtException -> throw LanglezException(401, "auth.invalid-token")
            else -> throw e
        }
    }

    fun createRefreshToken(id: Long, role: String): String {
        val now = Instant.now()
        val expiration = now.plus(Duration.ofSeconds(refreshTokenTTL))
        return Jwts.builder()
            .subject(id.toString())
            .claim("role", role)
            .claim("type", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key)
            .compact()
    }

    fun createAccessToken(id: Long, role: String): String {
        val now = Instant.now()
        val expiration = now.plus(Duration.ofSeconds(accessTokenTTL))
        return Jwts.builder()
            .subject(id.toString())
            .claim("role", role)
            .claim("type", "access")
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key)
            .compact()
    }

    fun extractId(claims: Claims): Long = claims.subject.toLong()

    fun extractRole(claims: Claims): String = claims.get("role", String::class.java)

    fun extractTokenType(claims: Claims): String = claims.get("type", String::class.java)

    fun extractRemainingValiditySeconds(claims: Claims): Long {
        val expirationInstant = claims.expiration.toInstant()
        val now = Instant.now()
        return Duration.between(now, expirationInstant).seconds
    }

    fun extractId(token: String): Long = extractId(parseClaims(token))

    fun extractRole(token: String): String = extractRole(parseClaims(token))

    fun extractTokenType(token: String): String = extractTokenType(parseClaims(token))

    fun extractRemainingValiditySeconds(token: String): Long = extractRemainingValiditySeconds(parseClaims(token))
}

@Deprecated("Use JwtTokenProvider instead", ReplaceWith("JwtTokenProvider"))
typealias JwtParser = JwtTokenProvider
