package com.langlez.auth.application

import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.utility.JwtTokenProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient

/** 정지/탈퇴한 회원이 계속 서비스를 쓰지 못하게 막는다. */
class AuthServiceStatusTest : BehaviorSpec({

    val jwt = mockk<JwtTokenProvider>()
    val memberService = mockk<MemberService>()
    val redisson = mockk<RedissonClient>()
    val bucket = mockk<RBucket<String>>(relaxed = true)
    val deviceBucket = mockk<RBucket<String>>(relaxed = true).also { every { it.get() } returns null }
    val tokenBlacklist = mockk<com.langlez.core.TokenBlacklist>()

    val service = AuthService(
        jwt, memberService, redisson, tokenBlacklist, mockk(relaxed = true),
        accessTokenTtlSecs = 3600,
        refreshTokenTtlSecs = 1209600,
    )

    fun member(status: Member.Status) = Member(
        id = 1L,
        email = "t@test.com",
        handle = "tester",
        status = status,
        provider = Member.Provider.GOOGLE,
        providerId = "p1",
    )

    fun stubValidRefresh(status: Member.Status) {
        every { jwt.extractTokenType("rt") } returns "refresh"
        every { jwt.extractId("rt") } returns 1L
        every { memberService.findById(1L) } returns member(status)
        every { redisson.getBucket<String>("refresh_token:1") } returns bucket
        every { redisson.getBucket<String>("refresh_device:1") } returns deviceBucket
        every { bucket.get() } returns "rt"
    }

    Given("정지된 회원이 토큰 갱신을 시도하면") {
        stubValidRefresh(Member.Status.SUSPENDED)

        Then("403 으로 거부된다") {
            val ex = shouldThrow<LanglezException> { service.refresh("rt") }
            ex.status.value() shouldBe 403
        }
    }

    Given("탈퇴한 회원이 토큰 갱신을 시도하면") {
        stubValidRefresh(Member.Status.WITHDRAWN)

        Then("403 으로 거부된다") {
            val ex = shouldThrow<LanglezException> { service.refresh("rt") }
            ex.status.value() shouldBe 403
        }
    }

    Given("정상 회원이 토큰 갱신을 시도하면") {
        stubValidRefresh(Member.Status.ACTIVE)
        every { jwt.createRefreshToken(1L, "tester", "ROLE_MEMBER") } returns "new-rt"
        every { jwt.createAccessToken(1L, "tester", "ROLE_MEMBER") } returns "new-at"

        Then("토큰이 재발급된다") {
            service.refresh("rt") shouldBe ("new-rt" to "new-at")
        }
    }
})
