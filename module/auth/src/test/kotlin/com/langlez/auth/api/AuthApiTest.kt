package com.langlez.auth.api

import com.langlez.auth.TestAuthApplication
import com.langlez.auth.application.AuthService
import com.langlez.auth.application.TokenProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(AuthApi::class)
@ContextConfiguration(classes = [TestAuthApplication::class, AuthApiTest.TestConfig::class])
class AuthApiTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @TestConfiguration
    class TestConfig {
        @Bean
        fun authService(): AuthService = mockk()

        @Bean
        fun tokenProvider(): TokenProvider = mockk()
    }

    @Autowired
    private lateinit var authService: AuthService

    init {
        test("Refresh Token으로 새로운 Access Token을 발급받을 수 있다") {
            // Given
            val refreshToken = "valid_refresh_token"
            val newAccessToken = "new_access_token"
            val newRefreshToken = "new_refresh_token"

            every { authService.refresh(refreshToken) } returns TokenResponse(newAccessToken, newRefreshToken)

            // When & Then
            mockMvc.post("/api/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken": "$refreshToken"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.accessToken") { value(newAccessToken) }
                jsonPath("$.refreshToken") { value(newRefreshToken) }
            }
        }
    }
}
