package com.langlez.auth.application

import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberOnlineTracker
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.utility.JwtTokenProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient

/**
 * 1인 1기기 정책. 새 기기에서 로그인하면 이전 기기 세션은 끊긴다.
 * 기기 식별은 클라이언트가 보내는 device id 로 한다.
 */
class AuthDeviceTest : BehaviorSpec({

    val jwt = mockk<JwtTokenProvider>()
    val memberService = mockk<MemberService>()
    val redisson = mockk<RedissonClient>()
    val tokenBlacklist = mockk<com.langlez.core.TokenBlacklist>()
    val recorder = mockk<MemberOnlineTracker>(relaxed = true)

    val tokenBucket = mockk<RBucket<String>>(relaxed = true)
    val deviceBucket = mockk<RBucket<String>>(relaxed = true)

    val service = AuthService(
        jwt, memberService, redisson, tokenBlacklist, recorder,
        accessTokenTtlSecs = 3600,
        refreshTokenTtlSecs = 1209600,
    )

    fun member() = Member(
        id = 1L,
        email = "t@test.com",
        handle = "tester",
        status = Member.Status.ACTIVE,
        provider = Member.Provider.GOOGLE,
        providerId = "p1",
    )

    every { redisson.getBucket<String>("refresh_token:1") } returns tokenBucket
    every { redisson.getBucket<String>("refresh_device:1") } returns deviceBucket
    every { jwt.createRefreshToken(1L, "tester", "ROLE_MEMBER") } returns "rt"
    every { jwt.createAccessToken(1L, "tester", "ROLE_MEMBER") } returns "at"

    Given("기기 A 에서 로그인하면") {
        service.issueTokens(1L, "tester", "ROLE_MEMBER", AccessContext("1.1.1.1", "device-A"))

        Then("그 기기 id 가 세션에 묶인다") {
            verify { deviceBucket.set("device-A", any()) }
        }

        Then("접속 IP/기기가 기록된다") {
            verify { recorder.recordAccess(1L, "1.1.1.1", "device-A") }
        }
    }

    Given("기기 A 의 리프레시 토큰으로 갱신할 때") {
        every { jwt.extractTokenType("rt") } returns "refresh"
        every { jwt.extractId("rt") } returns 1L
        every { memberService.findById(1L) } returns member()
        every { tokenBucket.get() } returns "rt"

        When("같은 기기에서 요청하면") {
            every { deviceBucket.get() } returns "device-A"

            Then("정상 갱신된다") {
                service.refresh("rt", AccessContext("1.1.1.1", "device-A")).second shouldBe "at"
            }
        }

        When("다른 기기에서 로그인해 세션이 넘어간 뒤라면") {
            every { deviceBucket.get() } returns "device-B"

            Then("401 로 거부된다") {
                val ex = shouldThrow<LanglezException> {
                    service.refresh("rt", AccessContext("2.2.2.2", "device-A"))
                }
                ex.status.value() shouldBe 401
            }
        }

        When("기기 id 를 아예 보내지 않으면") {
            every { deviceBucket.get() } returns "device-B"

            Then("검증을 건너뛰지 않고 401 로 거부된다") {
                // 헤더를 빼면 통과하는 fail-open 이면 탈취한 리프레시 토큰을 아무 기기에서나 쓸 수 있다
                val ex = shouldThrow<LanglezException> {
                    service.refresh("rt", AccessContext("2.2.2.2", null))
                }
                ex.status.value() shouldBe 401
            }
        }

        When("아직 바인딩된 기기가 없으면") {
            every { deviceBucket.get() } returns null

            Then("이번 기기로 바인딩하며 정상 갱신된다") {
                service.refresh("rt", AccessContext("1.1.1.1", "device-A")).second shouldBe "at"
                verify { deviceBucket.set("device-A", any()) }
            }
        }
    }
})
