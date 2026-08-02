package com.langlez.annotation

import com.langlez.exception.LanglezException
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

class MemberIdResolverTest {

    private val resolver = MemberIdResolver()

    private class SampleController {
        fun testMethod(@MemberId id: Long, notAnnotated: Long) {}
    }

    @Test
    fun `supportsParameter matches only MemberId annotated Long parameter`() {
        val method = SampleController::class.java.getDeclaredMethod("testMethod", Long::class.java, Long::class.java)

        val param1 = MethodParameter(method, 0)
        val param2 = MethodParameter(method, 1)

        assertTrue(resolver.supportsParameter(param1))
        assertTrue(!resolver.supportsParameter(param2))
    }

    @Test
    fun `resolveArgument resolves user id from security context`() {
        val authentication = UsernamePasswordAuthenticationToken(999L, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
        SecurityContextHolder.getContext().authentication = authentication

        try {
            val method = SampleController::class.java.getDeclaredMethod("testMethod", Long::class.java, Long::class.java)
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

        val method = SampleController::class.java.getDeclaredMethod("testMethod", Long::class.java, Long::class.java)
        val param = MethodParameter(method, 0)

        assertThrows(LanglezException::class.java) {
            resolver.resolveArgument(param, null, mockk<NativeWebRequest>(), null)
        }
    }
}
