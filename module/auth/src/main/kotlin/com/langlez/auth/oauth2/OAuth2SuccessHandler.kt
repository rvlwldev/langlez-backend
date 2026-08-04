package com.langlez.auth.oauth2

import com.langlez.auth.api.AuthCookies
import com.langlez.auth.application.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2SuccessHandler(
    private val service: AuthService,
    @param:Value($$"${app.oauth2.redirect-uri}") private val uri: String,
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(req: HttpServletRequest, res: HttpServletResponse, auth: Authentication) {
        val user = auth.principal as OAuth2LanglezUser
        val (refreshToken, accessToken) = service.issueTokens(user.id, user.handle, user.role)

        val accessCookie = AuthCookies.issue(AuthCookies.ACCESS_TOKEN, accessToken, service.accessTokenTtl)
        val refreshCookie = AuthCookies.issue(AuthCookies.REFRESH_TOKEN, refreshToken, service.refreshTokenTtl)

        res.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString())
        res.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString())

        clearAuthenticationAttributes(req)
        redirectStrategy.sendRedirect(req, res, uri)
    }
}