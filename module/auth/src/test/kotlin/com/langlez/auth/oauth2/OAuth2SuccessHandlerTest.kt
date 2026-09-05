package com.langlez.auth.oauth2

import com.langlez.auth.application.AccessContext
import com.langlez.auth.application.AuthService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication

/** 모바일 앱 전용. 쿠키를 쓰지 않고 앱 딥링크로 토큰을 돌려준다. */
class OAuth2SuccessHandlerTest : BehaviorSpec({

    val service = mockk<AuthService>()
    val redirectUri = "langlez://oauth2/callback"
    val handler = OAuth2SuccessHandler(service, redirectUri)

    val user = OAuth2LanglezUser(1L, "tester", "ROLE_MEMBER", mapOf("sub" to "123"), "sub")
    val auth = mockk<Authentication>()
    every { auth.principal } returns user

    Given("OAuth2 로그인에 성공하면") {
        every { service.issueTokens(1L, "tester", "ROLE_MEMBER", any()) } returns
            ("mock-refresh-token" to "mock-access-token")

        val req = MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.1"
            addHeader("X-Device-Id", "device-A")
        }
        val res = MockHttpServletResponse()

        handler.onAuthenticationSuccess(req, res, auth)

        Then("앱 딥링크로 리다이렉트한다") {
            res.redirectedUrl!! shouldContain redirectUri
        }

        Then("토큰이 쿼리 파라미터로 전달된다") {
            res.redirectedUrl!! shouldContain "accessToken=mock-access-token"
            res.redirectedUrl!! shouldContain "refreshToken=mock-refresh-token"
        }

        Then("쿠키는 설정하지 않는다") {
            res.cookies.size shouldBe 0
            res.getHeaders("Set-Cookie").size shouldBe 0
        }

        Then("기기 id 와 IP 가 함께 기록된다") {
            verify { service.issueTokens(1L, "tester", "ROLE_MEMBER", AccessContext("10.0.0.1", "device-A")) }
        }
    }

    Given("IdP 리다이렉트라 커스텀 헤더가 실리지 않은 콜백이면") {
        every { service.issueTokens(1L, "tester", "ROLE_MEMBER", any()) } returns ("r" to "a")

        // 로그인 시작 요청에서 OAuth2DeviceIdFilter 가 세션에 넣어 둔 값을 쓴다.
        val req = MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.2"
            session!!.setAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE, "device-B")
        }

        handler.onAuthenticationSuccess(req, MockHttpServletResponse(), auth)

        Then("로그인 시작 때 받아 둔 기기 id 로 세션을 묶는다") {
            verify { service.issueTokens(1L, "tester", "ROLE_MEMBER", AccessContext("10.0.0.2", "device-B")) }
        }

        Then("한 번 쓴 값은 세션에서 지운다") {
            req.session!!.getAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE) shouldBe null
        }
    }

    Given("기기 id 를 어디서도 못 받은 콜백이면") {
        every { service.issueTokens(1L, "tester", "ROLE_MEMBER", any()) } returns ("r" to "a")

        val req = MockHttpServletRequest().apply { remoteAddr = "10.0.0.3" }

        handler.onAuthenticationSuccess(req, MockHttpServletResponse(), auth)

        Then("기기 id 없이 발급한다") {
            // AuthService 가 이 경우 옛 바인딩을 지워 다음 갱신이 TOFU 로 다시 묶이게 한다.
            verify { service.issueTokens(1L, "tester", "ROLE_MEMBER", AccessContext("10.0.0.3", null)) }
        }
    }
})
