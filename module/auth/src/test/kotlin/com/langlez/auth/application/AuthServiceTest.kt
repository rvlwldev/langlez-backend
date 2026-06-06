package com.langlez.auth.application

import com.langlez.core.LanglezException
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import com.langlez.security.util.JwtParser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient
import org.springframework.http.HttpStatus
import java.util.concurrent.TimeUnit

class AuthServiceTest : BehaviorSpec({

    val jwt = mockk<JwtParser>()
    val memberService = mockk<MemberService>()
    val redisson = mockk<RedissonClient>()
    val bucket = mockk<RBucket<String>>()

    val service = AuthService(jwt, redisson, memberService)

    afterEach { clearMocks(jwt, memberService, redisson, bucket, answers = false) }

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

        every { redisson.getBucket<String>("refresh_token:$memberId") } returns bucket

        When("유효한 리프레시 토큰으로 갱신하면") {
            every { jwt.extractTokenType(validRefreshToken) } returns "refresh"
            every { jwt.extractID(validRefreshToken) } returns memberId
            every { memberService.findById(memberId) } returns member
            every { bucket.get() } returns validRefreshToken
            every { jwt.createRefreshToken(memberId, "MEMBER") } returns newRefreshToken
            every { jwt.createAccessToken(memberId, "MEMBER") } returns newAccessToken
            every { bucket.set(any(), any<Long>(), any<TimeUnit>()) } just runs

            Then("새로운 토큰 쌍이 반환되고 Redis에 저장된다") {
                val result = service.refresh(validRefreshToken)
                result.first shouldBe newRefreshToken
                result.second shouldBe newAccessToken
                verify { bucket.set(newRefreshToken, 14, TimeUnit.DAYS) }
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
            every { jwt.extractID(validRefreshToken) } returns 999L
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
            every { jwt.extractID("old-token") } returns memberId
            every { memberService.findById(memberId) } returns member
            every { bucket.get() } returns "different-token"

            Then("토큰 만료 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { service.refresh("old-token") }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.token-expired"
            }
        }

        When("Redis에 토큰이 없으면(만료)") {
            every { jwt.extractTokenType(validRefreshToken) } returns "refresh"
            every { jwt.extractID(validRefreshToken) } returns memberId
            every { memberService.findById(memberId) } returns member
            every { bucket.get() } returns null

            Then("토큰 만료 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { service.refresh(validRefreshToken) }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.token-expired"
            }
        }
    }
})
