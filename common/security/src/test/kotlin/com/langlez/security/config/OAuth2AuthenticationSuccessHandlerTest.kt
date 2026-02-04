package com.langlez.security.config

import com.langlez.security.token.JwtTokenProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.RedirectStrategy

class OAuth2AuthenticationSuccessHandlerTest : BehaviorSpec({
    val jwtTokenProvider = mockk<JwtTokenProvider>()
    val handler = OAuth2AuthenticationSuccessHandler(jwtTokenProvider)
    
    // AbstractAuthenticationTargetUrlRequestHandler(상위 클래스)의 리다이렉트 전략을 모킹합니다.
    // 이는 실제 HTTP 응답을 보내지 않고 리다이렉트 URL 생성 및 호출 여부만 검증하기 위함입니다.
    val redirectStrategy = mockk<RedirectStrategy>(relaxed = true)
    handler.setRedirectStrategy(redirectStrategy)

    Given("OAuth2 인증 성공 시") {
        val request = mockk<HttpServletRequest>(relaxed = true)
        val response = mockk<HttpServletResponse>(relaxed = true)
        val authentication = mockk<Authentication>()
        
        every { authentication.name } returns "test@example.com"
        every { response.isCommitted } returns false
        every { jwtTokenProvider.createToken("test@example.com") } returns "mock-jwt-token"

        When("핸들러가 호출되면") {
            handler.onAuthenticationSuccess(request, response, authentication)

            Then("JWT 토큰을 생성하고 프론트엔드로 리다이렉트해야 한다") {
                val urlSlot = slot<String>()
                verify { redirectStrategy.sendRedirect(request, response, capture(urlSlot)) }
                
                urlSlot.captured shouldContain "http://localhost:3000/oauth2/redirect"
                urlSlot.captured shouldContain "token=mock-jwt-token"
            }
        }
    }
})
