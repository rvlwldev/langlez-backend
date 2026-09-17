package com.langlez.auth.application

import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberAuthenticator
import com.langlez.member.contract.OnlineTracker
import com.langlez.security.TokenManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import java.util.Base64

/** 정지·탈퇴했거나 더 이상 존재하지 않는 회원이 계속 서비스를 쓰지 못하게 막는다. */
class AuthServiceStatusTest : BehaviorSpec({

    val secret = Base64.getEncoder().encodeToString("super-secret-key-12345678901234567890".toByteArray())

    // TokenManager 는 구체 클래스라 대역으로 갈지 않는다. 진짜 토큰을 발급해 서비스에 넘긴다.
    val tokens = TokenManager(secret, accessTokenTTL = 3600, refreshTokenTTL = 1209600, redisson = mockk(relaxed = true))

    val members = mockk<MemberAuthenticator>()
    val sessions = mockk<SessionStore>(relaxed = true)

    val service = AuthService(
        tokens, sessions, members, mockk<OnlineTracker>(relaxed = true),
        accessTokenTtlSecs = 3600,
        refreshTokenTtlSecs = 1209600,
    )

    val memberId = 1L
    val refreshToken = tokens.issueRefreshToken(memberId, "tester", "ROLE_MEMBER")

    every { sessions.boundDevice(memberId) } returns null
    every { sessions.rotate(memberId, from = refreshToken, to = any()) } returns true

    // findLoginable 은 회원이 없으면만 null 이다. 정지·탈퇴는 예외로 사유를 그대로 전파한다 —
    // MemberReader.findStatus 와 같은 이유로, 정지/탈퇴는 사용자에게 다른 문구를 내야 한다.
    Given("정지된 회원이 토큰 갱신을 시도하면") {
        every { members.findLoginable(memberId) } throws LanglezException(HttpStatus.FORBIDDEN, "member.suspended")

        Then("403 member.suspended 로 거부된다") {
            val ex = shouldThrow<LanglezException> { service.refresh(refreshToken, AccessContext()) }
            ex.status.value() shouldBe 403
            ex.message shouldBe "member.suspended"
        }
    }

    Given("탈퇴한 회원이 토큰 갱신을 시도하면") {
        every { members.findLoginable(memberId) } throws LanglezException(HttpStatus.FORBIDDEN, "member.withdrawn")

        Then("403 member.withdrawn 으로 거부된다") {
            val ex = shouldThrow<LanglezException> { service.refresh(refreshToken, AccessContext()) }
            ex.status.value() shouldBe 403
            ex.message shouldBe "member.withdrawn"
        }
    }

    Given("정상 회원이 토큰 갱신을 시도하면") {
        every { members.findLoginable(memberId) } returns
            MemberAuthenticator.AccountInfo(memberId, "tester", "ROLE_MEMBER")

        Then("토큰이 재발급된다") {
            val (newRefreshToken, newAccessToken) = service.refresh(refreshToken, AccessContext())

            tokens.parse(newRefreshToken).type shouldBe TokenManager.Type.REFRESH
            tokens.parse(newAccessToken).type shouldBe TokenManager.Type.ACCESS
            tokens.parse(newAccessToken).memberId shouldBe memberId
        }
    }
})
