package com.langlez.auth.oauth2

import com.langlez.security.util.JwtParser
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import java.util.concurrent.TimeUnit

class OAuth2SuccessHandlerTest : BehaviorSpec({

    val jwt = mockk<JwtParser>()
    val redisson = mockk<RedissonClient>()
    val bucket = mockk<RBucket<String>>()
    val redirectUri = "http://localhost:3000/oauth2/callback"

    val handler = OAuth2SuccessHandler(jwt, redisson, redirectUri)

    afterEach { clearMocks(jwt, redisson, bucket, answers = false) }

    Given("OAuth2 성공 로그인 시") {
        val memberId = 1L
        val role = "ROLE_MEMBER"
        val user = OAuth2LanglezUser(memberId, role, mapOf("sub" to "123"), "sub")
        val auth = mockk<Authentication>()

        every { auth.principal } returns user
        every { jwt.createRefreshToken(memberId, role) } returns "mock-refresh-token"
        every { jwt.createAccessToken(memberId, role) } returns "mock-access-token"
        every { redisson.getBucket<String>("refresh_token:$memberId") } returns bucket
        every { bucket.set("mock-refresh-token", 14, TimeUnit.DAYS) } just runs

        When("OAuth2SuccessHandler가 실행되면") {
            val req = MockHttpServletRequest()
            val res = MockHttpServletResponse()

            handler.onAuthenticationSuccess(req, res, auth)

            Then("토큰은 HttpOnly Secure 쿠키로 헤더에 설정되고 URL 파라미터에는 포함되지 않는다") {
                res.redirectedUrl shouldBe redirectUri

                val setCookieHeaders = res.getHeaders("Set-Cookie")
                setCookieHeaders.any { it.contains("accessToken=mock-access-token") && it.contains("HttpOnly") && it.contains("Secure") && it.contains("SameSite=Lax") } shouldBe true
                setCookieHeaders.any { it.contains("refreshToken=mock-refresh-token") && it.contains("HttpOnly") && it.contains("Secure") && it.contains("SameSite=Lax") } shouldBe true

                verify(exactly = 1) { bucket.set("mock-refresh-token", 14, TimeUnit.DAYS) }
            }
        }
    }
})
