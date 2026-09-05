package com.langlez.security

import com.langlez.exception.LanglezException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * 토큰의 발급·확인·차단을 한 곳에서 맡는다.
 *
 * 차단 저장은 Redisson 직결이다. `core.CacheProvider` 를 쓰면 Redis 장애 시 노드별 로컬 캐시로
 * 폴백해, A 노드가 무효화한 토큰을 B 노드가 못 보고 로그아웃·탈퇴한 토큰이 되살아난다.
 */
@Component
class TokenManager(
    @param:Value($$"${jwt.secret}") secret: String,
    @param:Value($$"${jwt.access-token-ttl-secs}") private val accessTokenTTL: Long,
    @param:Value($$"${jwt.refresh-token-ttl-secs}") private val refreshTokenTTL: Long,
    private val redisson: RedissonClient,
) {

    private val key: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))

    private val parser: JwtParser = Jwts.parser()
        .verifyWith(key)
        .build()

    fun issueAccessToken(id: Long, username: String, role: String): String =
        issue(id, username, role, Type.ACCESS, accessTokenTTL)

    fun issueRefreshToken(id: Long, username: String, role: String): String =
        issue(id, username, role, Type.REFRESH, refreshTokenTTL)

    fun parse(token: String): TokenInfo {
        val claims = try {
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

        return toInfo(claims)
    }

    /**
     * 남은 유효기간만큼만 차단 기록을 남긴다. TTL 은 호출자가 아니라 여기서 토큰을 파싱해 구한다.
     *
     * 이미 만료됐거나 서명이 깨진 토큰은 아무것도 저장하지 않고 조용히 끝낸다. 어차피 인증을
     * 통과하지 못하는 토큰이고, 음수 TTL 을 Redisson 에 넘기면 예외가 나거나 영구 키가 남는다.
     */
    fun revoke(token: String) {
        val expiresAt = try {
            parse(token).expiresAt
        } catch (e: LanglezException) {
            return
        }

        val remainingSeconds = Duration.between(Instant.now(), expiresAt).seconds
        if (remainingSeconds <= 0) return

        redisson.getBucket<String>(revocationKey(token)).set("1", Duration.ofSeconds(remainingSeconds))
    }

    fun isRevoked(token: String): Boolean = redisson.getBucket<String>(revocationKey(token)).isExists

    /**
     * `jti` 를 반드시 넣는다. `iat`/`exp` 는 초 단위라 같은 초에 같은 클레임으로 발급하면
     * **문자열이 완전히 같은 토큰**이 나온다. 리프레시 회전은 저장된 토큰을 새 토큰으로
     * 원자 교체하는데, 두 토큰이 같으면 교체가 성공한 것처럼 보이면서 실제로는 제자리라
     * 그 1초 안에 탈취된 토큰이 회전으로 무효화되지 않는다.
     *
     * 이미 발급된 토큰에는 `jti` 가 없지만 파싱은 이 클레임을 읽지 않으므로 그대로 통한다.
     */
    private fun issue(id: Long, username: String, role: String, type: Type, ttlSeconds: Long): String {
        val now = Instant.now()
        val expiration = now.plus(Duration.ofSeconds(ttlSeconds))

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(id.toString())
            .claim("username", username)
            .claim("role", role)
            .claim("type", type.name.lowercase())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key)
            .compact()
    }

    /**
     * 서명이 유효해도 클레임 구성이 우리 것과 다르면(다른 발급자의 키 재사용, 구 버전 토큰)
     * 여기서 터진다. 그 토큰으로는 어차피 인증할 수 없으니 파싱 실패와 같은 401 로 모은다.
     */
    private fun toInfo(claims: Claims): TokenInfo = try {
        TokenInfo(
            memberId = claims.subject.toLong(),
            username = claims.get("username", String::class.java),
            role = claims.get("role", String::class.java),
            type = Type.valueOf(claims.get("type", String::class.java).uppercase()),
            expiresAt = claims.expiration.toInstant(),
        )
    } catch (e: Exception) {
        throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.invalid-token", e)
    }

    data class TokenInfo(
        val memberId: Long,
        val username: String,
        val role: String,
        val type: Type,
        val expiresAt: Instant,
    )

    /** 클레임 값은 소문자다(`"access"` / `"refresh"`). 이미 발급된 토큰과 맞춰야 하니 바꾸지 않는다. */
    enum class Type { ACCESS, REFRESH }

    companion object {
        private fun revocationKey(token: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            return "blacklist:token:" + bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
