package com.langlez.auth.application

import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.security.TokenManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.redisson.api.RBucket
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import java.util.Base64

/** 정지/탈퇴한 회원이 계속 서비스를 쓰지 못하게 막는다. */
class AuthServiceStatusTest : BehaviorSpec({

    val secret = Base64.getEncoder().encodeToString("super-secret-key-12345678901234567890".toByteArray())

    // TokenManager 는 구체 클래스라 대역으로 갈지 않는다. 진짜 토큰을 발급해 서비스에 넘긴다.
    val tokens = TokenManager(secret, accessTokenTTL = 3600, refreshTokenTTL = 1209600, redisson = mockk(relaxed = true))

    val memberService = mockk<MemberService>()
    val redisson = mockk<RedissonClient>()
    val bucket = mockk<RBucket<String>>(relaxed = true)
    val deviceBucket = mockk<RBucket<String>>(relaxed = true).also { every { it.get() } returns null }

    val script = mockk<RScript>()
    every { redisson.getScript(StringCodec.INSTANCE) } returns script

    val service = AuthService(
        tokens, memberService, redisson, mockk(relaxed = true),
        accessTokenTtlSecs = 3600,
        refreshTokenTtlSecs = 1209600,
    )

    val refreshToken = tokens.issueRefreshToken(1L, "tester", "ROLE_MEMBER")

    fun member(status: Member.Status) = Member(
        id = 1L,
        email = "t@test.com",
        handle = "tester",
        status = status,
        provider = Member.Provider.GOOGLE,
        providerId = "p1",
    )

    fun stubValidRefresh(status: Member.Status) {
        every { memberService.findById(1L) } returns member(status)
        every { redisson.getBucket<String>("refresh_token:1", StringCodec.INSTANCE) } returns bucket
        every { redisson.getBucket<String>("refresh_device:1", StringCodec.INSTANCE) } returns deviceBucket
        // 회전은 Lua 스크립트 한 방이다. 저장값이 제시된 토큰과 같을 때만 1 을 돌려준다.
        every { script.eval<Long>(any<RScript.Mode>(), any<String>(), any<RScript.ReturnType>(), any<List<Any>>(), *varargAny { true }) } returns 1L
    }

    Given("정지된 회원이 토큰 갱신을 시도하면") {
        stubValidRefresh(Member.Status.SUSPENDED)

        Then("403 으로 거부된다") {
            val ex = shouldThrow<LanglezException> { service.refresh(refreshToken, AccessContext()) }
            ex.status.value() shouldBe 403
        }
    }

    Given("탈퇴한 회원이 토큰 갱신을 시도하면") {
        stubValidRefresh(Member.Status.WITHDRAWN)

        Then("403 으로 거부된다") {
            val ex = shouldThrow<LanglezException> { service.refresh(refreshToken, AccessContext()) }
            ex.status.value() shouldBe 403
        }
    }

    Given("정상 회원이 토큰 갱신을 시도하면") {
        stubValidRefresh(Member.Status.ACTIVE)

        Then("토큰이 재발급된다") {
            val (newRefreshToken, newAccessToken) = service.refresh(refreshToken, AccessContext())

            tokens.parse(newRefreshToken).type shouldBe TokenManager.Type.REFRESH
            tokens.parse(newAccessToken).type shouldBe TokenManager.Type.ACCESS
            tokens.parse(newAccessToken).memberId shouldBe 1L
        }
    }
})
