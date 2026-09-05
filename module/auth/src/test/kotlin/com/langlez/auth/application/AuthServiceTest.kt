package com.langlez.auth.application

import com.langlez.auth.domain.OAuth2UserProfile
import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.security.TokenManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient
import org.springframework.http.HttpStatus
import java.lang.reflect.InvocationTargetException
import java.time.Duration
import java.util.Base64

class AuthServiceTest : BehaviorSpec({

    val secret = Base64.getEncoder().encodeToString("super-secret-key-12345678901234567890".toByteArray())

    // TokenManager 는 구체 클래스라 대역으로 갈지 않는다. 진짜 토큰을 발급해 서비스에 넘긴다.
    val tokens = TokenManager(secret, accessTokenTTL = 3600, refreshTokenTTL = 1209600, redisson = mockk(relaxed = true))

    val memberService = mockk<MemberService>()
    val redisson = mockk<RedissonClient>()
    val bucket = mockk<RBucket<String>>()
    val deviceBucket = mockk<RBucket<String>>(relaxed = true).also { every { it.get() } returns null }

    val service = AuthService(
        tokens, memberService, redisson, mockk(relaxed = true),
        accessTokenTtlSecs = 3600,
        refreshTokenTtlSecs = 1209600,
    )

    afterEach { clearMocks(memberService, redisson, bucket, deviceBucket, answers = false) }

    Given("토큰 갱신 요청 시") {
        val memberId = 1L
        val member = Member(
            id = memberId,
            email = "test@example.com",
            handle = "tester",
            provider = Member.Provider.GOOGLE,
            providerId = "g123",
            providerDisplayName = "tester"
        )
        val validRefreshToken = tokens.issueRefreshToken(memberId, "tester", "ROLE_MEMBER")

        every { redisson.getBucket<String>("refresh_token:$memberId") } returns bucket
        every { redisson.getBucket<String>("refresh_device:$memberId") } returns deviceBucket

        When("유효한 리프레시 토큰으로 갱신하면") {
            every { memberService.findById(memberId) } returns member
            every { bucket.compareAndSet(validRefreshToken, any()) } returns true
            every { bucket.expire(any<Duration>()) } returns true

            Then("새로운 토큰 쌍이 반환되고 Redis에 저장된다") {
                val (refreshToken, accessToken) = service.refresh(validRefreshToken)

                tokens.parse(refreshToken).type shouldBe TokenManager.Type.REFRESH
                // 갱신 토큰의 role 도 최초 로그인과 같은 ROLE_ 접두사여야 한다. 안 그러면 hasRole 검사가 깨진다.
                tokens.parse(accessToken).role shouldBe "ROLE_MEMBER"
                tokens.parse(accessToken).type shouldBe TokenManager.Type.ACCESS

                // 회전은 원자 교체다. compareAndSet 의 SET 이 TTL 을 지우므로 곧바로 다시 건다.
                verify { bucket.compareAndSet(validRefreshToken, refreshToken) }
                verify { bucket.expire(Duration.ofDays(14)) }
            }
        }

        When("액세스 토큰으로 갱신을 시도하면") {
            val accessToken = tokens.issueAccessToken(memberId, "tester", "ROLE_MEMBER")

            Then("UNAUTHORIZED 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { service.refresh(accessToken) }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.invalid-token"
            }
        }

        When("존재하지 않는 회원의 토큰으로 갱신하면") {
            val orphanToken = tokens.issueRefreshToken(999L, "ghost", "ROLE_MEMBER")
            every { memberService.findById(999L) } returns null
            every { redisson.getBucket<String>("refresh_token:999") } returns bucket
            every { redisson.getBucket<String>("refresh_device:999") } returns deviceBucket

            Then("UNAUTHORIZED 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { service.refresh(orphanToken) }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.invalid-token"
            }
        }

        When("Redis에 저장된 토큰과 다른 토큰으로 갱신하면") {
            every { memberService.findById(memberId) } returns member
            every { bucket.compareAndSet(validRefreshToken, any()) } returns false
            every { bucket.delete() } returns true

            Then("토큰 만료 예외가 발생하고 세션은 지워지지 않는다") {
                val ex = shouldThrow<LanglezException> { service.refresh(validRefreshToken) }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.token-expired"

                // 불일치는 탈취뿐 아니라 "다른 요청이 방금 갱신했다" 는 뜻이기도 하다.
                // 지워버리면 동시 갱신이 정상 사용자를 재로그인시킨다.
                verify(exactly = 0) { bucket.delete() }
            }
        }

        When("Redis에 토큰이 없으면(만료)") {
            every { memberService.findById(memberId) } returns member
            every { bucket.compareAndSet(validRefreshToken, any()) } returns false
            every { bucket.delete() } returns true

            Then("토큰 만료 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { service.refresh(validRefreshToken) }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.token-expired"
            }
        }
    }

    Given("로그인 성공으로 토큰을 최초 발급할 때") {
        val memberId = 1L
        val handle = "tester"
        val role = "ROLE_MEMBER"

        every { redisson.getBucket<String>("refresh_token:$memberId") } returns bucket
        every { redisson.getBucket<String>("refresh_device:$memberId") } returns deviceBucket
        every { bucket.set(any(), any<Duration>()) } just runs

        When("issueTokens를 호출하면") {
            Then("토큰 쌍을 반환하고 refresh token을 Redis에 저장한다") {
                val (refreshToken, accessToken) = service.issueTokens(memberId, handle, role)

                tokens.parse(refreshToken).type shouldBe TokenManager.Type.REFRESH
                tokens.parse(accessToken).type shouldBe TokenManager.Type.ACCESS
                tokens.parse(accessToken).memberId shouldBe memberId

                verify { bucket.set(refreshToken, Duration.ofDays(14)) }
            }
        }
    }

    Given("탈퇴 이벤트를 받아 세션만 끊을 때") {
        val memberId = 1L
        every { redisson.getBucket<String>("refresh_token:$memberId") } returns bucket
        every { redisson.getBucket<String>("refresh_device:$memberId") } returns deviceBucket
        every { bucket.delete() } returns true
        every { deviceBucket.delete() } returns true

        When("invalidateSession을 호출하면") {
            Then("리프레시 토큰과 기기 바인딩이 지워진다") {
                service.invalidateSession(memberId)

                verify(exactly = 1) { bucket.delete() }
                verify(exactly = 1) { deviceBucket.delete() }
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
