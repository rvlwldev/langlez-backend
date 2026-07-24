package com.langlez.security.web

import com.langlez.core.LanglezException
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.NativeWebRequest

class MemberIdArgumentResolverTest {

    private val resolver = MemberIdArgumentResolver()

    @Suppress("DEPRECATION")
    private class SampleController {
        fun testMethod(@MemberId id: Long, @MemberID legacyId: Long, notAnnotated: Long) {}
    }

    @Test
    fun `supportsParameter matches MemberId and MemberID`() {
        val method = SampleController::class.java.getDeclaredMethod("testMethod", Long::class.java, Long::class.java, Long::class.java)

        val param1 = MethodParameter(method, 0)
        val param2 = MethodParameter(method, 1)
        val param3 = MethodParameter(method, 2)

        assertTrue(resolver.supportsParameter(param1))
        assertTrue(resolver.supportsParameter(param2))
        assertTrue(!resolver.supportsParameter(param3))
    }

    @Test
    fun `resolveArgument resolves user id from security context`() {
        val authentication = UsernamePasswordAuthenticationToken(999L, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
        SecurityContextHolder.getContext().authentication = authentication

        try {
            val method = SampleController::class.java.getDeclaredMethod("testMethod", Long::class.java, Long::class.java, Long::class.java)
            val param = MethodParameter(method, 0)

            val resolved = resolver.resolveArgument(param, null, mockk<NativeWebRequest>(), null)
            assertEquals(999L, resolved)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    @Test
    fun `resolveArgument throws 401 when security context is empty`() {
        SecurityContextHolder.clearContext()

        val method = SampleController::class.java.getDeclaredMethod("testMethod", Long::class.java, Long::class.java, Long::class.java)
        val param = MethodParameter(method, 0)

        assertThrows(LanglezException::class.java) {
            resolver.resolveArgument(param, null, mockk<NativeWebRequest>(), null)
        }
    }
}
