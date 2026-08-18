package com.langlez.auth.api

import com.langlez.auth.api.AuthRequest.RefreshToken
import com.langlez.auth.application.AccessContext
import com.langlez.auth.application.AuthService
import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest

/** 모바일 전용이라 쿠키 경로가 없다. refresh 는 바디, access 는 Authorization 헤더로만 받는다. */
class AuthControllerTest : BehaviorSpec({

    val service = mockk<AuthService>()
    val controller = AuthController(service)

    fun servletRequest(remote: String = "10.0.0.1", forwarded: String? = null) =
        mockk<HttpServletRequest>(relaxed = true).also {
            every { it.remoteAddr } returns remote
            every { it.getHeader("X-Forwarded-For") } returns forwarded
        }

    afterEach { clearMocks(service, answers = false) }

    Given("토큰 재발급 요청 시") {
        When("바디로 유효한 refreshToken 을 보내면") {
            every { service.refresh("valid-refresh-token", any()) } returns
                Pair("new-refresh-token", "new-access-token")

            Then("새 토큰이 바디로 반환되고, 기기 id 와 IP 가 서비스로 전달된다") {
                val result = controller.refresh(RefreshToken("valid-refresh-token"), servletRequest(), "device-A")

                result.refreshToken shouldBe "new-refresh-token"
                result.accessToken shouldBe "new-access-token"
                verify { service.refresh("valid-refresh-token", AccessContext("10.0.0.1", "device-A")) }
            }
        }

        When("프록시를 거쳐 X-Forwarded-For 가 있으면") {
            every { service.refresh(any(), any()) } returns Pair("r", "a")

            Then("원 클라이언트 IP 를 쓴다") {
                controller.refresh(
                    RefreshToken("t"),
                    servletRequest(remote = "10.0.0.1", forwarded = "203.0.113.7, 10.0.0.9"),
                    "device-A",
                )
                verify { service.refresh("t", AccessContext("203.0.113.7", "device-A")) }
            }
        }

        When("토큰이 유효하지 않으면") {
            every { service.refresh("bad", any()) } throws LanglezException(401, "auth.invalid-token")

            Then("예외가 그대로 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.refresh(RefreshToken("bad"), servletRequest(), "device-A")
                }
                ex.status.value() shouldBe 401
            }
        }
    }

    Given("기기 id 없이 재발급을 요청하면") {
        Then("400 으로 거부된다") {
            // 헤더를 빼는 것만으로 1인 1기기 검증을 우회할 수 없어야 한다
            val ex = shouldThrow<LanglezException> {
                controller.refresh(RefreshToken("t"), servletRequest(), null)
            }
            ex.status.value() shouldBe 400
        }

        Then("빈 문자열도 거부된다") {
            val ex = shouldThrow<LanglezException> {
                controller.refresh(RefreshToken("t"), servletRequest(), "  ")
            }
            ex.status.value() shouldBe 400
        }
    }

    Given("로그아웃 요청 시") {
        When("Bearer 토큰을 보내면") {
            every { service.logout(1L, "access-token") } returns Unit

            Then("서비스로 위임된다") {
                controller.logout(1L, "Bearer access-token")
                verify { service.logout(1L, "access-token") }
            }
        }

        When("Bearer 형식이 아니면") {
            Then("400 이 발생한다") {
                val ex = shouldThrow<LanglezException> { controller.logout(1L, "access-token") }
                ex.status.value() shouldBe 400
            }
        }
    }
})
