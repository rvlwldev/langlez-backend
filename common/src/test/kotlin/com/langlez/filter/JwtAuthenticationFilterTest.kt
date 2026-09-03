package com.langlez.filter

import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberReader
import com.langlez.security.TokenManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerExceptionResolver
import java.util.Base64

class JwtAuthenticationFilterTest {

    // TokenManager 는 구체 클래스라 대역으로 갈지 않는다. 진짜 토큰을 발급해 필터에 태운다.
    private val bucket = mockk<RBucket<String>>(relaxed = true)
    private val redisson = mockk<RedissonClient>().also { every { it.getBucket<String>(any<String>()) } returns bucket }
    private val tokens = TokenManager(SECRET, accessTokenTTL = 3600, refreshTokenTTL = 86400, redisson = redisson)

    private val resolver = mockk<HandlerExceptionResolver>(relaxed = true)
    private val members = mockk<MemberReader>()

    private val filter = JwtAuthenticationFilter(tokens, members, resolver)

    private val req = mockk<HttpServletRequest>(relaxed = true)
    private val res = mockk<HttpServletResponse>(relaxed = true)
    private val chain = mockk<FilterChain>(relaxed = true)

    @BeforeEach
    fun setUp() {
        // OncePerRequestFilter 는 이 속성이 non-null 이면 doFilterInternal 을 건너뛴다.
        // relaxed mock 의 기본 반환값은 non-null 이라 명시적으로 null 을 돌려줘야 필터 본문이 돈다.
        every { req.getAttribute(any()) } returns null
        every { req.dispatcherType } returns DispatcherType.REQUEST
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun givenValidToken() {
        every { req.getHeader("Authorization") } returns
            "Bearer " + tokens.issueAccessToken(1L, "tester", "ROLE_MEMBER")
        every { bucket.isExists } returns false
        every { members.findStatus(1L) } returns MemberReader.Status.ACTIVE
    }

    private fun anonymousToken() =
        AnonymousAuthenticationToken("key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))

    @Test
    fun `인증 정보는 하류 체인이 실행되는 동안 보여야 한다`() {
        givenValidToken()
        var seen: Authentication? = null
        every { chain.doFilter(req, res) } answers { seen = SecurityContextHolder.getContext().authentication }

        filter.doFilter(req, res, chain)

        assertNotNull(seen, "체인 실행 시점에 인증이 없으면 모든 인가 검사가 무력화된다")
        assertEquals(1L, seen?.principal)
    }

    /**
     * 이 필터는 ExceptionTranslationFilter 와 AuthorizationFilter 사이에 있다.
     * AccessDeniedException 이 이 필터를 거쳐 올라가는 동안 컨텍스트를 비우면,
     * ExceptionTranslationFilter 가 authentication == null 을 보고 isAnonymous 를 false 로 판정해
     * 미인증 요청에 401 대신 403 을 준다.
     */
    @Test
    fun `예외가 통과할 때 기존 익명 인증을 지우지 않는다`() {
        every { req.getHeader("Authorization") } returns null
        SecurityContextHolder.getContext().authentication = anonymousToken()
        every { chain.doFilter(req, res) } throws AccessDeniedException("denied")

        assertThrows<AccessDeniedException> { filter.doFilter(req, res, chain) }

        assertNotNull(
            SecurityContextHolder.getContext().authentication,
            "익명 토큰이 사라지면 미인증 요청이 401 대신 403 을 받는다"
        )
    }

    @Test
    fun `하류의 AccessDeniedException을 삼키지 않고 그대로 전파한다`() {
        givenValidToken()
        every { chain.doFilter(req, res) } throws AccessDeniedException("denied")

        assertThrows<AccessDeniedException> { filter.doFilter(req, res, chain) }

        // 필터가 가로채면 ExceptionTranslationFilter 가 못 받고 accessDeniedHandler 가 안 돈다
        verify(exactly = 0) { resolver.resolveException(any(), any(), any(), any()) }
    }

    @Test
    fun `토큰 파싱 실패는 이 필터가 resolver 로 넘긴다`() {
        every { req.getHeader("Authorization") } returns "Bearer broken"
        every { bucket.isExists } returns false

        filter.doFilter(req, res, chain)

        verify { resolver.resolveException(req, res, null, any<LanglezException>()) }
        verify(exactly = 0) { chain.doFilter(req, res) }
    }

    @Test
    fun `차단된 토큰은 인증 없이 401 로 넘긴다`() {
        every { req.getHeader("Authorization") } returns
            "Bearer " + tokens.issueAccessToken(1L, "tester", "ROLE_MEMBER")
        every { bucket.isExists } returns true

        filter.doFilter(req, res, chain)

        verify { resolver.resolveException(req, res, null, any<LanglezException>()) }
        verify(exactly = 0) { chain.doFilter(req, res) }
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `refresh 토큰으로는 인증되지 않는다`() {
        every { req.getHeader("Authorization") } returns
            "Bearer " + tokens.issueRefreshToken(1L, "tester", "ROLE_MEMBER")
        every { bucket.isExists } returns false

        filter.doFilter(req, res, chain)

        verify { resolver.resolveException(req, res, null, any<LanglezException>()) }
        verify(exactly = 0) { chain.doFilter(req, res) }
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `Authorization 헤더가 없으면 인증 없이 체인을 계속 태운다`() {
        every { req.getHeader("Authorization") } returns null

        filter.doFilter(req, res, chain)

        verify { chain.doFilter(req, res) }
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    companion object {
        private val SECRET =
            Base64.getEncoder().encodeToString("super-secret-key-12345678901234567890".toByteArray())
    }
}
