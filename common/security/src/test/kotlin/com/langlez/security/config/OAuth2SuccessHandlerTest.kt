package com.langlez.security.config

import com.langlez.security.token.JwtTokenProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.RedirectStrategy

import org.springframework.data.redis.core.StringRedisTemplate

class OAuth2SuccessHandlerTest : BehaviorSpec({
    val jwtTokenProvider = mockk<JwtTokenProvider>()
    val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
    val redirectUri = "http://localhost:3000/oauth2/redirect"
    val handler = OAuth2SuccessHandler(jwtTokenProvider, redisTemplate, redirectUri)
    
    val redirectStrategy = mockk<RedirectStrategy>(relaxed = true)
    handler.setRedirectStrategy(redirectStrategy)

    Given("OAuth2 인증 성공 시") {
        val request = mockk<HttpServletRequest>(relaxed = true)
        val response = mockk<HttpServletResponse>(relaxed = true)
        val authentication = mockk<Authentication>()
        val oAuth2User = mockk<OAuth2User>()
        
        every { authentication.principal } returns oAuth2User
        every { oAuth2User.attributes } returns mapOf("email" to "test@example.com")
        
        // Role 추출 로직 테스트를 위한 Authority 설정
        every { authentication.authorities } returns listOf(SimpleGrantedAuthority("ROLE_MEMBER"))
        
        every { response.isCommitted } returns false
        
        every { jwtTokenProvider.createAccessToken("test@example.com", "ROLE_MEMBER") } returns "mock-access-token"
        every { jwtTokenProvider.createRefreshToken("test@example.com") } returns "mock-refresh-token"

        When("핸들러가 호출되면") {
            handler.onAuthenticationSuccess(request, response, authentication)

            Then("JWT 토큰을 생성하고 프론트엔드로 리다이렉트해야 한다") {
                val urlSlot = slot<String>()
                verify { redirectStrategy.sendRedirect(request, response, capture(urlSlot)) }
                
                urlSlot.captured shouldContain redirectUri
                urlSlot.captured shouldContain "accessToken=mock-access-token"
                urlSlot.captured shouldContain "refreshToken=mock-refresh-token"
            }
        }
    }
})
