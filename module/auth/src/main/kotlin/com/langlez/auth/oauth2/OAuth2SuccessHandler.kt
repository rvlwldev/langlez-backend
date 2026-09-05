package com.langlez.auth.oauth2

import com.langlez.auth.api.AuthController
import com.langlez.auth.application.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * 모바일 앱 전용이라 쿠키를 쓰지 않는다.
 * 앱이 열어둔 딥링크(`app.oauth2.redirect-uri`)로 토큰을 쿼리 파라미터에 실어 돌려준다.
 */
@Component
class OAuth2SuccessHandler(
    private val service: AuthService,
    @param:Value($$"${app.oauth2.redirect-uri}") private val uri: String,
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(req: HttpServletRequest, res: HttpServletResponse, auth: Authentication) {
        val user = auth.principal as OAuth2LanglezUser

        // IdP 리다이렉트라 커스텀 헤더가 남지 않는다. 로그인 시작 때 OAuth2DeviceIdFilter 가
        // 세션에 넣어 둔 값을 쓴다. 한 번 쓰고 지운다 — 다음 로그인이 옛 기기를 물려받으면 안 된다.
        val session = req.getSession(false)
        val deviceId = session?.getAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE) as? String
            ?: req.getHeader(AuthController.DEVICE_ID_HEADER)
        session?.removeAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE)

        val ctx = AuthController.accessContext(req, deviceId)

        val (refreshToken, accessToken) = service.issueTokens(user.id, user.handle, user.role, ctx)

        val target = UriComponentsBuilder.fromUriString(uri)
            .queryParam("accessToken", accessToken)
            .queryParam("refreshToken", refreshToken)
            .build()
            .toUriString()

        clearAuthenticationAttributes(req)
        redirectStrategy.sendRedirect(req, res, target)
    }
}
