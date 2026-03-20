package com.langlez.auth.application

import com.langlez.exception.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRepository
import com.langlez.security.util.JwtParser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import java.util.concurrent.TimeUnit

class AuthServiceTest : BehaviorSpec({

    val jwt = mockk<JwtParser>()
    val repo = mockk<MemberRepository>()
    val redis = mockk<StringRedisTemplate>()
    val valueOps = mockk<ValueOperations<String, String>>()

    every { redis.opsForValue() } returns valueOps

    val service = AuthService(jwt, repo, redis, mockk())

    afterEach { clearMocks(jwt, repo, valueOps, answers = false) }

    Given("토큰 갱신 요청 시") {
        val memberId = 1L
        val member = Member(
            id = memberId,
            email = "test@example.com",
            nickname = "tester",
            provider = MemberProvider("g123", MemberProvider.Type.GOOGLE, "tester")
        )
        val validRefreshToken = "valid-refresh-token"
        val newRefreshToken = "new-refresh-token"
        val newAccessToken = "new-access-token"

        When("유효한 리프레시 토큰으로 갱신하면") {
            every { jwt.extractTokenType(validRefreshToken) } returns "refresh"
            every { jwt.extractID(validRefreshToken) } returns memberId
            every { repo.findById(memberId) } returns member
            every { valueOps.get("refresh_token:$memberId") } returns validRefreshToken
            every { jwt.createRefreshToken(memberId, "MEMBER") } returns newRefreshToken
            every { jwt.createAccessToken(memberId, "MEMBER") } returns newAccessToken
            every { valueOps.set(any(), any(), any<Long>(), any<TimeUnit>()) } just runs

            Then("새로운 토큰 쌍이 반환되고 Redis에 저장된다") {
                val result = service.refresh(validRefreshToken)
                result.first shouldBe newRefreshToken
                result.second shouldBe newAccessToken
                verify { valueOps.set("refresh_token:$memberId", newRefreshToken, 14, TimeUnit.DAYS) }
            }
        }

        When("액세스 토큰으로 갱신을 시도하면") {
            every { jwt.extractTokenType("access-token") } returns "access"

            Then("UNAUTHORIZED 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.refresh("access-token")
                }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.invalid-token"
            }
        }

        When("존재하지 않는 회원의 토큰으로 갱신하면") {
            every { jwt.extractTokenType(validRefreshToken) } returns "refresh"
            every { jwt.extractID(validRefreshToken) } returns 999L
            every { repo.findById(999L) } returns null

            Then("UNAUTHORIZED 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.refresh(validRefreshToken)
                }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.invalid-token"
            }
        }

        When("Redis에 저장된 토큰과 다른 토큰으로 갱신하면") {
            every { jwt.extractTokenType("old-token") } returns "refresh"
            every { jwt.extractID("old-token") } returns memberId
            every { repo.findById(memberId) } returns member
            every { valueOps.get("refresh_token:$memberId") } returns "different-token"

            Then("토큰 만료 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.refresh("old-token")
                }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.token-expired"
            }
        }

        When("Redis에 토큰이 없으면(만료)") {
            every { jwt.extractTokenType(validRefreshToken) } returns "refresh"
            every { jwt.extractID(validRefreshToken) } returns memberId
            every { repo.findById(memberId) } returns member
            every { valueOps.get("refresh_token:$memberId") } returns null

            Then("토큰 만료 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.refresh(validRefreshToken)
                }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.token-expired"
            }
        }
    }
})
