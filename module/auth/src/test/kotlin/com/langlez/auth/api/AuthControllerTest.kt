package com.langlez.auth.api

import com.langlez.auth.api.AuthRequest.RefreshToken
import com.langlez.auth.application.AuthService
import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.time.Duration

class AuthControllerTest : BehaviorSpec({

    val service = mockk<AuthService>()
    val controller = AuthController(service)

    beforeEach {
        every { service.accessTokenTtl } returns Duration.ofHours(1)
        every { service.refreshTokenTtl } returns Duration.ofDays(14)
    }

    afterEach { clearMocks(service, answers = false) }

    Given("토큰 재발급 요청 시") {
        When("바디로 유효한 refreshToken을 보내면") {
            every { service.refresh("valid-refresh-token") } returns Pair("new-refresh-token", "new-access-token")

            Then("새로 발급된 refreshToken/accessToken이 반환된다") {
                val result = controller.refresh(null, RefreshToken("valid-refresh-token"))
                result.body?.refreshToken shouldBe "new-refresh-token"
                result.body?.accessToken shouldBe "new-access-token"
            }
        }

        When("HttpOnly 쿠키로 refreshToken을 보내면") {
            every { service.refresh("cookie-refresh-token") } returns Pair("new-refresh-token", "new-access-token")

            val result = controller.refresh("cookie-refresh-token", null)

            Then("쿠키 값으로 재발급되고 응답에 갱신된 토큰 쿠키가 실린다") {
                result.body?.accessToken shouldBe "new-access-token"

                val cookies = result.headers[HttpHeaders.SET_COOKIE]!!
                cookies shouldHaveSize 2
                cookies.first { it.startsWith("accessToken=") } shouldContain "new-access-token"
                cookies.first { it.startsWith("refreshToken=") } shouldContain "new-refresh-token"
            }
        }

        When("쿠키와 바디 둘 다 있으면") {
            every { service.refresh("cookie-token") } returns Pair("r", "a")

            controller.refresh("cookie-token", RefreshToken("body-token"))

            Then("쿠키를 우선한다") {
                verify(exactly = 1) { service.refresh("cookie-token") }
                verify(exactly = 0) { service.refresh("body-token") }
            }
        }

        When("쿠키도 바디도 없으면") {
            Then("400 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { controller.refresh(null, null) }
                ex.status shouldBe HttpStatus.BAD_REQUEST
            }
        }

        When("유효하지 않은 refreshToken이면") {
            every { service.refresh("invalid-token") } throws LanglezException(401, "auth.invalid-token")

            Then("401 예외가 그대로 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.refresh(null, RefreshToken("invalid-token"))
                }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.invalid-token"
            }
        }

        When("만료된 refreshToken이면") {
            every { service.refresh("expired-token") } throws LanglezException(401, "auth.token-expired")

            Then("401 예외가 그대로 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.refresh(null, RefreshToken("expired-token"))
                }
                ex.status shouldBe HttpStatus.UNAUTHORIZED
                ex.message shouldBe "auth.token-expired"
            }
        }
    }

    Given("로그아웃 요청 시") {
        When("memberId와 Authorization 헤더로 로그아웃하면") {
            val token = "some-access-token"
            every { service.logout(1L, token) } just runs

            val result = controller.logout(1L, "Bearer $token")

            Then("서비스의 logout이 호출되고 토큰 쿠키 삭제 헤더가 실린다") {
                verify(exactly = 1) { service.logout(1L, token) }

                val cookies = result.headers[HttpHeaders.SET_COOKIE]!!
                cookies shouldHaveSize 2
                cookies.forEach { it shouldContain "Max-Age=0" }
            }
        }
    }
})
