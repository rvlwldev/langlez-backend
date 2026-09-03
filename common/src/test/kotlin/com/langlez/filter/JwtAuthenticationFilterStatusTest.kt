package com.langlez.filter

import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberReader
import com.langlez.security.TokenManager
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerExceptionResolver
import java.util.Base64

/**
 * 정지·탈퇴 회원의 액세스 토큰 즉시 차단.
 *
 * 상태 검사가 로그인·토큰 갱신에만 있으면 계정을 정지시켜도 이미 발급된 토큰이 만료될 때까지
 * 모든 API 가 그대로 통과한다. 그 구멍이 다시 열리지 않도록 요청 경로에서 고정한다.
 */
class JwtAuthenticationFilterStatusTest : BehaviorSpec({

    val secret = Base64.getEncoder().encodeToString("super-secret-key-12345678901234567890".toByteArray())

    // TokenManager 는 구체 클래스라 대역으로 갈지 않는다. 진짜 토큰을 발급해 필터에 태운다.
    val bucket = mockk<RBucket<String>>(relaxed = true).also { every { it.isExists } returns false }
    val redisson = mockk<RedissonClient>().also { every { it.getBucket<String>(any<String>()) } returns bucket }
    val tokens = TokenManager(secret, accessTokenTTL = 3600, refreshTokenTTL = 86400, redisson = redisson)

    val members = mockk<MemberReader>()
    val resolver = mockk<HandlerExceptionResolver>(relaxed = true)

    val filter = JwtAuthenticationFilter(tokens, members, resolver)

    val res = mockk<HttpServletResponse>(relaxed = true)
    val chain = mockk<FilterChain>(relaxed = true)

    // 호출 기록만 지운다. 스텁까지 지우면 Then 블록 사이에 돌아 뒤 블록이 빈 스텁을 본다.
    afterEach {
        clearMocks(bucket, redisson, members, resolver, chain, answers = false)
        SecurityContextHolder.clearContext()
    }

    fun accessToken() = tokens.issueAccessToken(1L, "tester", "ROLE_MEMBER")

    fun request(uri: String = "/api/v1/members/me", token: String? = accessToken()): HttpServletRequest {
        val req = mockk<HttpServletRequest>(relaxed = true)
        // OncePerRequestFilter 는 이 속성이 non-null 이면 doFilterInternal 을 통째로 건너뛴다.
        // relaxed mock 의 기본 반환값은 non-null 이라 명시적으로 null 을 돌려줘야 필터 본문이 돈다.
        every { req.getAttribute(any()) } returns null
        every { req.dispatcherType } returns DispatcherType.REQUEST
        every { req.requestURI } returns uri
        every { req.getHeader("Authorization") } returns token?.let { "Bearer $it" }

        return req
    }

    fun rejection(req: HttpServletRequest): LanglezException {
        val captured = slot<Exception>()
        every { resolver.resolveException(req, res, null, capture(captured)) } returns null

        filter.doFilter(req, res, chain)

        verify(exactly = 0) { chain.doFilter(req, res) }
        return captured.captured as LanglezException
    }

    Given("액세스 토큰을 들고 일반 API 를 호출할 때") {

        When("회원이 ACTIVE 면") {
            val req = request()
            every { members.findStatus(1L) } returns MemberReader.Status.ACTIVE

            Then("체인을 그대로 통과한다") {
                filter.doFilter(req, res, chain)

                verify { chain.doFilter(req, res) }
                verify(exactly = 0) { resolver.resolveException(any(), any(), any(), any()) }
            }
        }

        When("회원이 SUSPENDED 면") {
            val req = request()
            every { members.findStatus(1L) } returns MemberReader.Status.SUSPENDED

            Then("403 member.suspended 로 거부한다") {
                val e = rejection(req)

                e.status.value() shouldBe 403
                e.message shouldBe "member.suspended"
            }

            Then("인증이 수립되지 않는다") {
                filter.doFilter(req, res, chain)

                SecurityContextHolder.getContext().authentication.shouldBeNull()
            }
        }

        When("회원이 WITHDRAWN 이면") {
            val req = request()
            every { members.findStatus(1L) } returns MemberReader.Status.WITHDRAWN

            Then("403 member.withdrawn 로 거부한다") {
                val e = rejection(req)

                e.status.value() shouldBe 403
                e.message shouldBe "member.withdrawn"
            }
        }

        When("토큰이 가리키는 회원이 없으면") {
            val req = request()
            every { members.findStatus(1L) } returns null

            Then("401 auth.invalid-token 으로 다시 로그인시킨다") {
                val e = rejection(req)

                e.status.value() shouldBe 401
                e.message shouldBe "auth.invalid-token"
            }
        }

        // 가입 직후 상태가 CREATED 이고 이를 ACTIVE 로 올리는 Member.verify() 를 부르는
        // 엔드포인트가 아직 없다. 여기서 막으면 신규 가입자가 전부 잠긴다.
        When("회원이 CREATED 면") {
            val req = request()
            every { members.findStatus(1L) } returns MemberReader.Status.CREATED

            Then("막지 않고 통과시킨다") {
                filter.doFilter(req, res, chain)

                verify { chain.doFilter(req, res) }
            }
        }
    }

    Given("정지된 회원이 인증 엔드포인트를 호출할 때") {

        When("로그아웃을 요청하면") {
            val req = request(uri = "/api/v1/auth/logout")
            every { members.findStatus(1L) } returns MemberReader.Status.SUSPENDED

            Then("리프레시 토큰과 기기 바인딩을 정리할 수 있게 통과시킨다") {
                filter.doFilter(req, res, chain)

                verify { chain.doFilter(req, res) }
                verify(exactly = 0) { resolver.resolveException(any(), any(), any(), any()) }
            }

            Then("상태 조회 자체를 하지 않는다") {
                filter.doFilter(req, res, chain)

                verify(exactly = 0) { members.findStatus(any()) }
            }
        }
    }

    Given("토큰이 없는 미인증 요청일 때") {

        When("일반 API 를 호출하면") {
            val req = request(token = null)

            // 여기서 거부하면 401 이어야 할 응답이 403 으로 바뀐다.
            // 미인증 판정은 ExceptionTranslationFilter 의 authenticationEntryPoint 몫이다.
            Then("상태를 조회하지 않고 인증 없이 체인을 계속 태운다") {
                filter.doFilter(req, res, chain)

                verify { chain.doFilter(req, res) }
                verify(exactly = 0) { members.findStatus(any()) }
                verify(exactly = 0) { resolver.resolveException(any(), any(), any(), any()) }
                SecurityContextHolder.getContext().authentication.shouldBeNull()
            }
        }
    }
})
