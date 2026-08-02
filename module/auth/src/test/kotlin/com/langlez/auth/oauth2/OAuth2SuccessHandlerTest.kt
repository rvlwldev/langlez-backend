package com.langlez.auth.oauth2

import com.langlez.auth.application.AuthService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication

class OAuth2SuccessHandlerTest : BehaviorSpec({

    val service = mockk<AuthService>()
    val redirectUri = "http://localhost:3000/oauth2/callback"

    val handler = OAuth2SuccessHandler(service, redirectUri)

    afterEach { clearMocks(service, answers = false) }

    Given("OAuth2 성공 로그인 시") {
        val memberId = 1L
        val handle = "tester"
        val role = "ROLE_MEMBER"
        val user = OAuth2LanglezUser(memberId, handle, role, mapOf("sub" to "123"), "sub")
        val auth = mockk<Authentication>()

        every { auth.principal } returns user
        every { service.issueTokens(memberId, handle, role) } returns ("mock-refresh-token" to "mock-access-token")

        When("OAuth2SuccessHandler가 실행되면") {
            val req = MockHttpServletRequest()
            val res = MockHttpServletResponse()

            handler.onAuthenticationSuccess(req, res, auth)

            Then("토큰은 HttpOnly Secure 쿠키로 헤더에 설정되고 URL 파라미터에는 포함되지 않는다") {
                res.redirectedUrl shouldBe redirectUri

                val setCookieHeaders = res.getHeaders("Set-Cookie")
                setCookieHeaders.any { it.contains("accessToken=mock-access-token") && it.contains("HttpOnly") && it.contains("Secure") && it.contains("SameSite=Lax") } shouldBe true
                setCookieHeaders.any { it.contains("refreshToken=mock-refresh-token") && it.contains("HttpOnly") && it.contains("Secure") && it.contains("SameSite=Lax") } shouldBe true
            }
        }
    }
})
