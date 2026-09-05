package com.langlez.auth.oauth2

import com.langlez.auth.api.AuthController
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * OAuth2 로그인 시작 요청에 실린 기기 식별자를 세션에 옮겨 둔다.
 *
 * 콜백은 IdP 가 만든 리다이렉트라 앱이 붙인 `X-Device-Id` 헤더가 남지 않는다. 그래서
 * `OAuth2SuccessHandler` 는 기기를 알 수 없고, 기기 바인딩이 이전 기기로 남아 새 기기의 첫
 * 갱신이 401 로 잘린다. 기기를 알 수 있는 마지막 지점이 로그인 시작 요청이다.
 *
 * **`state` 에 싣지 않는다.** `state` 는 CSRF 방어용이고 클라이언트와 IdP 를 왕복하므로, 값을
 * 실으면 돌아온 문자열을 신뢰해야 한다. 기기 바인딩은 보안 판정이라 위조 가능한 입력을 근거로
 * 삼을 수 없다. 여기서는 값이 서버 밖으로 나가지 않고, Spring 이 authorization request 를
 * 보관하는 것과 같은 HttpSession 에만 머문다 — 세션이 다르면 콜백은 authorization request 가
 * 없어 그 전에 거부된다. 기기 id 자체는 여전히 클라이언트가 주장하는 값이지만, 그건
 * `X-Device-Id` 헤더를 받는 기존 경로와 같은 신뢰 수준(TOFU)이고 이 필터가 신뢰를 더 늘리지 않는다.
 *
 * 시큐리티 필터 체인(order -100)보다 먼저 돌아야 한다. 로그인 시작 요청은 체인 안의
 * `OAuth2AuthorizationRequestRedirectFilter` 가 리다이렉트로 끝내버려서, 기본 순서로 두면
 * 이 필터는 아예 실행되지 않는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class OAuth2DeviceIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        // requestURI 가 아니라 servletPath 다. context-path 가 붙으면 requestURI 는 그것까지 포함해
        // 접두사 비교가 조용히 어긋난다.
        if (req.servletPath.startsWith(AUTHORIZATION_BASE_URI)) {
            // 공백만 든 값을 넣으면 바인딩이 그 문자열로 잡혀 1인 1기기 판정이 무의미해진다.
            val deviceId = (req.getParameter(DEVICE_ID_PARAM) ?: req.getHeader(AuthController.DEVICE_ID_HEADER))
                ?.takeIf { it.isNotBlank() }

            // 기기 id 가 없으면 남아 있던 값을 지운다. 앞선 시도가 동의 화면에서 취소되면
            // SuccessHandler 가 안 불려 그 기기 id 가 세션에 남고, 같은 세션의 다음 로그인이
            // 그걸 물려받아 엉뚱한 기기로 바인딩된다. 로그인은 반드시 이 경로에서 시작하므로
            // 취소·이탈·타임아웃을 가리지 않고 여기서 한 번에 정리된다.
            if (deviceId != null) req.getSession(true).setAttribute(SESSION_ATTRIBUTE, deviceId)
            else req.getSession(false)?.removeAttribute(SESSION_ATTRIBUTE)
        }

        chain.doFilter(req, res)
    }

    companion object {
        /** 앱이 웹뷰로 여는 URL 이라 커스텀 헤더를 못 붙일 수 있다. 쿼리 파라미터를 먼저 본다. */
        const val DEVICE_ID_PARAM = "deviceId"
        const val SESSION_ATTRIBUTE = "OAUTH2_DEVICE_ID"

        /** Spring Security 의 `OAuth2AuthorizationRequestRedirectFilter` 기본 진입 경로. */
        private const val AUTHORIZATION_BASE_URI = "/oauth2/authorization/"
    }
}
