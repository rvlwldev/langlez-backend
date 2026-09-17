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
import org.springframework.http.HttpStatus
import java.util.Base64

class AuthServiceTest : BehaviorSpec({

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

    afterEach { clearMocks(members, sessions, tracker, answers = false) }

    fun account(id: Long = 1L) = MemberAuthenticator.AccountInfo(id, "tester", "ROLE_MEMBER")

    Given("토큰 갱신 요청 시") {
        val memberId = 1L
        val validRefreshToken = tokens.issueRefreshToken(memberId, "tester", "ROLE_MEMBER")

        every { sessions.boundDevice(memberId) } returns null

        When("유효한 리프레시 토큰으로 갱신하면") {
            every { members.findLoginable(memberId) } returns account(memberId)
            every { sessions.rotate(memberId, from = validRefreshToken, to = any()) } returns true

            Then("새로운 토큰 쌍이 반환되고 세션이 회전한다") {
                val (refreshToken, accessToken) = service.refresh(validRefreshToken, AccessContext())

                tokens.parse(refreshToken).type shouldBe TokenManager.Type.REFRESH
                // 갱신 토큰의 role 도 최초 로그인과 같은 ROLE_ 접두사여야 한다. 안 그러면 hasRole 검사가 깨진다.
                tokens.parse(accessToken).role shouldBe "ROLE_MEMBER"
                tokens.parse(accessToken).type shouldBe TokenManager.Type.ACCESS

                verify { sessions.rotate(memberId, from = validRefreshToken, to = refreshToken) }
                verify { sessions.bindDevice(memberId, null) }
            }
        }

        When("액세스 토큰으로 갱신을 시도하면") {
            val accessToken = tokens.issueAccessToken(memberId, "tester", "ROLE_MEMBER")

            Then("UNAUTHORIZED 예외가 발생하고 회원 조회는 일어나지 않는다") {
                val ex = shouldThrow<LanglezException> { service.refresh(accessToken, AccessContext()) }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.invalid-token"

                verify(exactly = 0) { members.findLoginable(any()) }
            }
        }

        When("세션 저장소에 저장된 토큰과 다른 토큰으로 갱신하면(회전 실패)") {
            every { members.findLoginable(memberId) } returns account(memberId)
            every { sessions.rotate(memberId, from = validRefreshToken, to = any()) } returns false

            Then("토큰 만료 예외가 발생하고 세션은 건드리지 않는다") {
                val ex = shouldThrow<LanglezException> { service.refresh(validRefreshToken, AccessContext()) }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.token-expired"

                // 불일치는 탈취뿐 아니라 "다른 요청이 방금 갱신했다" 는 뜻이기도 하다.
                // 회전이 실패했으면 기기 바인딩도 세션도 건드리지 않는다.
                verify(exactly = 0) { sessions.bindDevice(any(), any()) }
                verify(exactly = 0) { sessions.close(any()) }
            }
        }
    }

    Given("로그인 성공으로 토큰을 최초 발급할 때") {
        val memberId = 1L
        val handle = "tester"
        val role = "ROLE_MEMBER"

        When("issueTokens를 호출하면") {
            Then("토큰 쌍을 반환하고 세션을 연다") {
                val (refreshToken, accessToken) =
                    service.issueTokens(memberId, handle, role, AccessContext("1.1.1.1", "device-A"))

                tokens.parse(refreshToken).type shouldBe TokenManager.Type.REFRESH
                tokens.parse(accessToken).type shouldBe TokenManager.Type.ACCESS
                tokens.parse(accessToken).memberId shouldBe memberId

                verify { sessions.open(memberId, refreshToken, "device-A") }
                verify { tracker.recordAccess(memberId, "1.1.1.1", "device-A") }
            }
        }
    }

    Given("탈퇴 이벤트를 받아 세션만 끊을 때") {
        val memberId = 1L

        When("invalidateSession을 호출하면") {
            Then("세션 저장소에 세션 종료를 위임한다") {
                service.invalidateSession(memberId)

                verify(exactly = 1) { sessions.close(memberId) }
            }
        }
    }
})
