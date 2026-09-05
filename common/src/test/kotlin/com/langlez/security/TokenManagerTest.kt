package com.langlez.security

import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * 발급·확인과 차단을 나눠 보던 두 스펙을 합쳤다.
 *
 * 차단 저장은 레디스를 띄우지 않고 Redisson 호출을 검증한다. 배포 시점에 이미 차단된 토큰이
 * 풀리지 않으려면 **키 문자열이 그대로여야** 하는데, 실제 레디스에 넣었다 빼는 왕복은
 * 어떤 키를 썼든 통과해서 그걸 못 잡는다.
 */
class TokenManagerTest : BehaviorSpec({

    val secret = Base64.getEncoder().encodeToString("super-secret-key-12345678901234567890".toByteArray())

    val bucket = mockk<RBucket<String>>(relaxed = true)
    val redisson = mockk<RedissonClient>()
    every { redisson.getBucket<String>(any<String>()) } returns bucket

    val tokens = TokenManager(secret, accessTokenTTL = 3600, refreshTokenTTL = 86400, redisson = redisson)

    // 호출 기록만 지운다. 스텁까지 지우면 Then 블록 사이에 돌아 뒤 블록이 빈 스텁을 본다.
    afterEach { clearMocks(bucket, redisson, answers = false) }

    fun revocationKey(token: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return "blacklist:token:" + bytes.joinToString("") { "%02x".format(it) }
    }

    Given("액세스 토큰을 발급하면") {
        val token = tokens.issueAccessToken(123L, "tester", "ROLE_MEMBER")

        Then("담아 보낸 클레임 그대로 되읽힌다") {
            val info = tokens.parse(token)

            info.memberId shouldBe 123L
            info.username shouldBe "tester"
            info.role shouldBe "ROLE_MEMBER"
            info.type shouldBe TokenManager.Type.ACCESS
        }

        Then("만료 시각이 TTL 만큼 뒤에 있다") {
            val remaining = Duration.between(Instant.now(), tokens.parse(token).expiresAt)

            (remaining.seconds in 3590..3600) shouldBe true
        }
    }

    Given("리프레시 토큰을 발급하면") {
        val token = tokens.issueRefreshToken(456L, "tester", "ROLE_ADMIN")

        Then("타입이 REFRESH 로 구분된다") {
            val info = tokens.parse(token)

            info.memberId shouldBe 456L
            info.role shouldBe "ROLE_ADMIN"
            info.type shouldBe TokenManager.Type.REFRESH
        }
    }

    Given("서명이 맞지 않는 토큰을 파싱하면") {
        Then("401 auth.invalid-token 이다") {
            val e = shouldThrow<LanglezException> { tokens.parse("invalid.jwt.token") }

            e.status.value() shouldBe 401
            e.message shouldBe "auth.invalid-token"
        }
    }

    Given("이미 만료된 토큰을 파싱하면") {
        val expiredTokens = TokenManager(secret, accessTokenTTL = -60, refreshTokenTTL = -60, redisson = redisson)
        val expired = expiredTokens.issueAccessToken(1L, "tester", "ROLE_MEMBER")

        Then("401 auth.token-expired 로 구분된다") {
            val e = shouldThrow<LanglezException> { tokens.parse(expired) }

            e.status.value() shouldBe 401
            e.message shouldBe "auth.token-expired"
        }
    }

    Given("유효한 토큰을 차단하면") {
        val token = tokens.issueAccessToken(1L, "tester", "ROLE_MEMBER")

        Then("SHA-256 해시 키에 남은 유효기간만큼만 저장한다") {
            tokens.revoke(token)

            verify { redisson.getBucket<String>(revocationKey(token)) }
            verify {
                bucket.set(
                    "1",
                    match<Duration> { it.seconds in 3590..3600 },
                )
            }
        }
    }

    Given("이미 만료된 토큰을 차단하면") {
        val expiredTokens = TokenManager(secret, accessTokenTTL = -60, refreshTokenTTL = -60, redisson = redisson)
        val expired = expiredTokens.issueAccessToken(1L, "tester", "ROLE_MEMBER")

        // 음수 TTL 을 Redisson 에 넘기면 예외가 나거나 영구 키가 남는다. 둘 다 나쁘다.
        Then("예외 없이 아무것도 저장하지 않는다") {
            tokens.revoke(expired)

            verify(exactly = 0) { bucket.set(any(), any<Duration>()) }
        }
    }

    Given("서명이 깨진 토큰을 차단하면") {
        Then("예외 없이 아무것도 저장하지 않는다") {
            tokens.revoke("invalid.jwt.token")

            verify(exactly = 0) { bucket.set(any(), any<Duration>()) }
        }
    }

    Given("차단 여부를 확인할 때") {
        val token = tokens.issueAccessToken(1L, "tester", "ROLE_MEMBER")

        When("같은 해시 키가 살아 있으면") {
            every { bucket.isExists } returns true

            Then("차단된 것으로 본다") {
                tokens.isRevoked(token) shouldBe true
                verify { redisson.getBucket<String>(revocationKey(token)) }
            }
        }

        When("키가 없으면") {
            every { bucket.isExists } returns false

            Then("차단되지 않은 것으로 본다") {
                tokens.isRevoked(token) shouldBe false
            }
        }
    }

    Given("같은 초에 같은 클레임으로 두 번 발급하면") {
        Then("토큰 문자열이 서로 다르다") {
            // iat/exp 는 초 단위라 jti 가 없으면 완전히 같은 문자열이 나온다. 그러면 리프레시
            // 회전이 제자리 교체가 돼, 그 1초 안에 탈취된 토큰이 무효화되지 않는다.
            val first = tokens.issueRefreshToken(1L, "tester", "ROLE_MEMBER")
            val second = tokens.issueRefreshToken(1L, "tester", "ROLE_MEMBER")

            first shouldNotBe second
        }

        Then("두 토큰 다 정상 파싱된다") {
            val token = tokens.issueRefreshToken(1L, "tester", "ROLE_MEMBER")

            tokens.parse(token).memberId shouldBe 1L
            tokens.parse(token).type shouldBe TokenManager.Type.REFRESH
        }
    }
})
