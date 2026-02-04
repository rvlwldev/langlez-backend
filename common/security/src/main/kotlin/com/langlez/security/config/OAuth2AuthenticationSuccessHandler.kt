package com.langlez.security.config

import com.langlez.security.token.JwtTokenProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2AuthenticationSuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
) : SimpleUrlAuthenticationSuccessHandler() {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val targetUrl = determineTargetUrl(request, response, authentication)

        if (response.isCommitted) {
            logger.debug("Response has already been committed. Unable to redirect to $targetUrl")
            return
        }

        clearAuthenticationAttributes(request)
        redirectStrategy.sendRedirect(request, response, targetUrl)
    }

    override fun determineTargetUrl(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ): String {
        // In a real app, the default redirect URL should be configured (e.g. frontend URL)
        // For now, we redirect to a simple endpoint that displays the token or back to localhost
        val targetUrl = "http://localhost:3000/oauth2/redirect"

        // We use the 'name' (which we mapped to email or providerId usually) as the subject for now
        // Or better, we should have the Member ID. But CustomOAuth2User doesn't hold ID yet.
        // Let's assume name is email for now.
        val token = jwtTokenProvider.createToken(authentication.name)

        return UriComponentsBuilder
            .fromUriString(targetUrl)
            .queryParam("token", token)
            .build()
            .toUriString()
    }
}
