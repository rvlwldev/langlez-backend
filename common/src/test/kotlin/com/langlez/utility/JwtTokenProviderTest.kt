package com.langlez.utility

import com.langlez.exception.LanglezException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Base64

class JwtTokenProviderTest {

    private val secret = Base64.getEncoder().encodeToString("super-secret-key-12345678901234567890".toByteArray())
    private val provider = JwtTokenProvider(secret = secret, accessTokenTTL = 3600, refreshTokenTTL = 86400)

    @Test
    fun `create and parse access token`() {
        val token = provider.createAccessToken(123L, "tester", "ROLE_USER")
        assertNotNull(token)

        val claims = provider.parseToClaims(token)
        assertEquals(123L, provider.extractId(claims))
        assertEquals("ROLE_USER", provider.extractRole(claims))
        assertEquals("access", provider.extractTokenType(claims))

        assertEquals(123L, provider.extractId(token))
        assertEquals("ROLE_USER", provider.extractRole(token))
        assertEquals("access", provider.extractTokenType(token))
    }

    @Test
    fun `create and parse refresh token`() {
        val token = provider.createRefreshToken(456L, "tester", "ROLE_ADMIN")
        assertNotNull(token)

        val claims = provider.parseToClaims(token)
        assertEquals(456L, provider.extractId(claims))
        assertEquals("ROLE_ADMIN", provider.extractRole(claims))
        assertEquals("refresh", provider.extractTokenType(claims))
    }

    @Test
    fun `invalid token throws exception`() {
        assertThrows(LanglezException::class.java) {
            provider.parseToClaims("invalid.jwt.token")
        }
    }
}
