package com.langlez.auth.application

import com.langlez.auth.domain.OAuth2UserProfile
import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.utility.JwtTokenProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient
import org.springframework.http.HttpStatus
import java.lang.reflect.InvocationTargetException
import java.time.Duration

class AuthServiceTest : BehaviorSpec({

    val jwt = mockk<JwtTokenProvider>()
    val memberService = mockk<MemberService>()
    val redisson = mockk<RedissonClient>()
    val bucket = mockk<RBucket<String>>()
    val tokenBlacklist = mockk<com.langlez.core.TokenBlacklist>()

    val service = AuthService(jwt, memberService, redisson, tokenBlacklist)

    afterEach { clearMocks(jwt, memberService, redisson, bucket, tokenBlacklist, answers = false) }

    Given("토큰 갱신 요청 시") {
        val memberId = 1L
        val member = Member(
            id = memberId,
            email = "test@example.com",
            username = "tester",
            nickname = "tester",
            provider = Member.Provider.GOOGLE,
            providerId = "g123",
            providerDisplayName = "tester"
        )
        val validRefreshToken = "valid-refresh-token"
        val newRefreshToken = "new-refresh-token"
        val newAccessToken = "new-access-token"

        every { redisson.getBucket<String>("refresh_token:$memberId") } returns bucket

        When("유효한 리프레시 토큰으로 갱신하면") {
            every { jwt.extractTokenType(validRefreshToken) } returns "refresh"
            every { jwt.extractId(validRefreshToken) } returns memberId
            every { memberService.findById(memberId) } returns member
            every { bucket.get() } returns validRefreshToken
            every { jwt.createRefreshToken(memberId, "tester", "MEMBER") } returns newRefreshToken
            every { jwt.createAccessToken(memberId, "tester", "MEMBER") } returns newAccessToken
            every { bucket.set(any(), any<Duration>()) } just runs

            Then("새로운 토큰 쌍이 반환되고 Redis에 저장된다") {
                val result = service.refresh(validRefreshToken)
                result.first shouldBe newRefreshToken
                result.second shouldBe newAccessToken
                verify { bucket.set(newRefreshToken, Duration.ofDays(14)) }
            }
        }

        When("액세스 토큰으로 갱신을 시도하면") {
            every { jwt.extractTokenType("access-token") } returns "access"

            Then("UNAUTHORIZED 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { service.refresh("access-token") }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.invalid-token"
            }
        }

        When("존재하지 않는 회원의 토큰으로 갱신하면") {
            every { jwt.extractTokenType(validRefreshToken) } returns "refresh"
            every { jwt.extractId(validRefreshToken) } returns 999L
            every { memberService.findById(999L) } returns null
            every { redisson.getBucket<String>("refresh_token:999") } returns bucket

            Then("UNAUTHORIZED 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { service.refresh(validRefreshToken) }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.invalid-token"
            }
        }

        When("Redis에 저장된 토큰과 다른 토큰으로 갱신하면") {
            every { jwt.extractTokenType("old-token") } returns "refresh"
            every { jwt.extractId("old-token") } returns memberId
            every { memberService.findById(memberId) } returns member
            every { bucket.get() } returns "different-token"
            every { bucket.delete() } returns true

            Then("토큰 만료 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { service.refresh("old-token") }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.token-expired"
                verify(exactly = 1) { bucket.delete() }
            }
        }

        When("Redis에 토큰이 없으면(만료)") {
            every { jwt.extractTokenType(validRefreshToken) } returns "refresh"
            every { jwt.extractId(validRefreshToken) } returns memberId
            every { memberService.findById(memberId) } returns member
            every { bucket.get() } returns null

            Then("토큰 만료 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { service.refresh(validRefreshToken) }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.token-expired"
            }
        }
    }

    Given("로그인 성공으로 토큰을 최초 발급할 때") {
        val memberId = 1L
        val username = "tester"
        val role = "MEMBER"

        every { redisson.getBucket<String>("refresh_token:$memberId") } returns bucket
        every { jwt.createRefreshToken(memberId, username, role) } returns "issued-refresh-token"
        every { jwt.createAccessToken(memberId, username, role) } returns "issued-access-token"
        every { bucket.set(any(), any<Duration>()) } just runs

        When("issueTokens를 호출하면") {
            Then("토큰 쌍을 반환하고 refresh token을 Redis에 저장한다") {
                val result = service.issueTokens(memberId, username, role)
                result.first shouldBe "issued-refresh-token"
                result.second shouldBe "issued-access-token"
                verify { bucket.set("issued-refresh-token", Duration.ofDays(14)) }
            }
        }
    }

    // oauth2Login()은 private이라 리플렉션으로 직접 호출
    Given("OAuth2 로그인 요청 시") {
        When("신규 회원 가입 중 이메일이 누락된 프로필이면") {
            val profile = OAuth2UserProfile.by("google", "sub", mapOf("sub" to "g123"))
            every { memberService.findByProvider(Member.Provider.GOOGLE, "g123") } returns null

            Then("400 예외가 발생한다") {
                val method = AuthService::class.java.getDeclaredMethod("oauth2Login", OAuth2UserProfile::class.java)
                method.isAccessible = true

                val invocationEx = shouldThrow<InvocationTargetException> {
                    method.invoke(service, profile)
                }
                val ex = invocationEx.cause as LanglezException
                ex.status shouldBe HttpStatus.BAD_REQUEST
                ex.message shouldBe "auth.invalid-request"
            }
        }
    }
})
