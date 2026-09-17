package com.langlez.auth.application

import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberAuthenticator
import com.langlez.member.contract.OnlineTracker
import com.langlez.security.TokenManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Base64

/**
 * 1인 1기기 정책. 새 기기에서 로그인하면 이전 기기 세션은 끊긴다.
 * 기기 식별은 클라이언트가 보내는 device id 로 한다.
 */
class AuthDeviceTest : BehaviorSpec({

    val secret = Base64.getEncoder().encodeToString("super-secret-key-12345678901234567890".toByteArray())

    // TokenManager 는 구체 클래스라 대역으로 갈지 않는다. 진짜 토큰을 발급해 서비스에 넘긴다.
    val tokens = TokenManager(secret, accessTokenTTL = 3600, refreshTokenTTL = 1209600, redisson = mockk(relaxed = true))

    val members = mockk<MemberAuthenticator>()
    val sessions = mockk<SessionStore>(relaxed = true)
    val tracker = mockk<OnlineTracker>(relaxed = true)

    val service = AuthService(
        tokens, sessions, members, tracker,
        accessTokenTtlSecs = 3600,
        refreshTokenTtlSecs = 1209600,
    )

    val memberId = 1L
    val refreshToken = tokens.issueRefreshToken(memberId, "tester", "ROLE_MEMBER")

    every { members.findLoginable(memberId) } returns MemberAuthenticator.AccountInfo(memberId, "tester", "ROLE_MEMBER")
    every { sessions.rotate(memberId, from = refreshToken, to = any()) } returns true

    afterEach { clearMocks(sessions, tracker, answers = false) }

    Given("기기 A 에서 로그인하면") {
        Then("그 기기 id 가 세션에 묶이고 접속 IP/기기가 기록된다") {
            service.issueTokens(memberId, "tester", "ROLE_MEMBER", AccessContext("1.1.1.1", "device-A"))

            verify { sessions.open(memberId, any(), "device-A") }
            verify { tracker.recordAccess(memberId, "1.1.1.1", "device-A") }
        }
    }

    Given("기기 A 의 리프레시 토큰으로 갱신할 때") {
        When("같은 기기에서 요청하면") {
            every { sessions.boundDevice(memberId) } returns "device-A"

            Then("정상 갱신된다") {
                val accessToken = service.refresh(refreshToken, AccessContext("1.1.1.1", "device-A")).second

                tokens.parse(accessToken).memberId shouldBe memberId
            }
        }

        When("다른 기기에서 로그인해 세션이 넘어간 뒤라면") {
            every { sessions.boundDevice(memberId) } returns "device-B"

            Then("401 로 거부되고 회전을 시도하지 않는다") {
                // 밀려난 기기의 토큰은 이미 회전으로 무효라, 기기 검사가 회전보다 앞이면
                // 회전 자체를 시도하지 않는다. AuthService 에서 이 순서를 뒤집으면 아래
                // verify(exactly = 0) 이 빨간불이 된다 — 직접 뒤집어 확인한 결과는 PR 본문 참고.
                val ex = shouldThrow<LanglezException> {
                    service.refresh(refreshToken, AccessContext("2.2.2.2", "device-A"))
                }
                ex.status.value() shouldBe 401
                ex.message shouldBe "auth.session-taken-over"

                verify(exactly = 0) { sessions.rotate(any(), any(), any()) }
            }
        }

        When("기기 id 를 아예 보내지 않으면") {
            every { sessions.boundDevice(memberId) } returns "device-B"

            Then("검증을 건너뛰지 않고 401 로 거부되며 회전을 시도하지 않는다") {
                // 헤더를 빼면 통과하는 fail-open 이면 탈취한 리프레시 토큰을 아무 기기에서나 쓸 수 있다
                val ex = shouldThrow<LanglezException> {
                    service.refresh(refreshToken, AccessContext("2.2.2.2", null))
                }
                ex.status.value() shouldBe 401

                verify(exactly = 0) { sessions.rotate(any(), any(), any()) }
            }
        }

        When("아직 바인딩된 기기가 없으면") {
            every { sessions.boundDevice(memberId) } returns null

            Then("이번 기기로 바인딩하며 정상 갱신된다") {
                val accessToken = service.refresh(refreshToken, AccessContext("1.1.1.1", "device-A")).second

                tokens.parse(accessToken).memberId shouldBe memberId
                verify { sessions.bindDevice(memberId, "device-A") }
            }
        }
    }
})
