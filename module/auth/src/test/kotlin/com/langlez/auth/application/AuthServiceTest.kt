import com.langlez.auth.api.TokenResponse
import com.langlez.common.exception.LanglezException
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.security.token.JwtTokenProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.DisplayName
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

@DisplayName("AuthService: 토큰 갱신 로직 테스트")
class AuthServiceTest :
        BehaviorSpec({
            val tokenProvider = mockk<JwtTokenProvider>()
            val memberService = mockk<MemberService>()
            val redisTemplate = mockk<StringRedisTemplate>()
            val valueOperations = mockk<ValueOperations<String, String>>()
            val authService = AuthService(tokenProvider, memberService, redisTemplate)

            beforeContainer {
                clearMocks(tokenProvider, memberService, redisTemplate, valueOperations)
                every { redisTemplate.opsForValue() } returns valueOperations
            }

            Given("유효한 refresh token으로 갱신 요청 시") {
                val refreshToken = "valid_refresh_token"
                val email = "test@example.com"
                val member = mockk<Member> { every { role } returns Member.Role.MEMBER }

                every { tokenProvider.getEmail(refreshToken) } returns email
                every { valueOperations.get("refresh_token:$email") } returns refreshToken
                every { tokenProvider.validateToken(refreshToken) } returns true
                coEvery { memberService.getMember(email) } returns member
                every { tokenProvider.createAccessToken(email, "ROLE_MEMBER") } returns "new_access_token"
                every { tokenProvider.createRefreshToken(email) } returns "new_refresh_token"
                every { redisTemplate.delete("refresh_token:$email") } returns true
                every { valueOperations.set("refresh_token:$email", "new_refresh_token", 14, TimeUnit.DAYS) } just Runs

                When("토큰 갱신을 요청하면") {
                    val result = authService.refresh(refreshToken)

                    Then("새로운 access token과 refresh token을 반환해야 한다") {
                        result.accessToken shouldBe "new_access_token"
                        result.refreshToken shouldBe "new_refresh_token"
                    }
                }
            }

            Given("잘못된 JWT 형식의 refresh token 입력 시") {
                val invalidToken = "invalid_jwt_token"
                every { tokenProvider.getEmail(invalidToken) } throws RuntimeException("Invalid JWT")

                When("토큰 갱신을 요청하면") {
                    Then("LanglezException을 던져야 한다") {
                        shouldThrow<LanglezException> { authService.refresh(invalidToken) }
                    }
                }
            }

            Given("Redis에 저장된 토큰과 다른 토큰으로 요청 시") {
                val refreshToken = "different_token"
                val email = "test@example.com"

                every { tokenProvider.getEmail(refreshToken) } returns email
                every { valueOperations.get("refresh_token:$email") } returns "stored_token"

                When("토큰 갱신을 요청하면") {
                    Then("LanglezException을 던져야 한다") {
                        shouldThrow<LanglezException> { authService.refresh(refreshToken) }
                    }
                }
            }

            Given("JWT 서명이 유효하지 않은 경우") {
                val refreshToken = "invalid_signature_token"
                val email = "test@example.com"

                every { tokenProvider.getEmail(refreshToken) } returns email
                every { valueOperations.get("refresh_token:$email") } returns refreshToken
                every { tokenProvider.validateToken(refreshToken) } returns false
                every { redisTemplate.delete("refresh_token:$email") } returns true

                When("토큰 갱신을 요청하면") {
                    Then("Redis에서 토큰을 삭제하고 LanglezException을 던져야 한다") {
                        shouldThrow<LanglezException> { authService.refresh(refreshToken) }
                        verify(exactly = 1) { redisTemplate.delete("refresh_token:$email") }
                    }
                }
            }
        })
