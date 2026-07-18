package com.langlez.auth.api

import com.langlez.auth.api.AuthRequest.RefreshToken
import com.langlez.auth.application.AuthService
import com.langlez.core.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class AuthControllerTest : BehaviorSpec({

    val service = mockk<AuthService>()
    val controller = AuthController(service)

    afterEach { clearMocks(service, answers = false) }

    Given("토큰 재발급 요청 시") {
        When("유효한 refreshToken으로 서비스가 정상 처리하면") {
            every { service.refresh("valid-refresh-token") } returns Pair("new-refresh-token", "new-access-token")

            Then("새로 발급된 refreshToken/accessToken이 반환된다") {
                val result = controller.refresh(RefreshToken("valid-refresh-token"))
                result.refreshToken shouldBe "new-refresh-token"
                result.accessToken shouldBe "new-access-token"
            }
        }

        When("유효하지 않은 refreshToken이면") {
            every { service.refresh("invalid-token") } throws LanglezException(401, "auth.invalid-token")

            Then("401 예외가 그대로 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.refresh(RefreshToken("invalid-token"))
                }
                ex.status shouldBe 401
                ex.message shouldBe "auth.invalid-token"
            }
        }

        When("만료된 refreshToken이면") {
            every { service.refresh("expired-token") } throws LanglezException(401, "auth.token-expired")

            Then("401 예외가 그대로 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.refresh(RefreshToken("expired-token"))
                }
                ex.status shouldBe 401
                ex.message shouldBe "auth.token-expired"
            }
        }
    }

    Given("로그아웃 요청 시") {
        When("memberId와 Authorization 헤더로 로그아웃하면") {
            val token = "some-access-token"
            every { service.logout(1L, token) } just runs

            Then("서비스의 logout이 정확히 호출된다") {
                controller.logout(1L, "Bearer $token")
                verify(exactly = 1) { service.logout(1L, token) }
            }
        }
    }
})
