package com.langlez.auth.oauth2

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.security.SecurityProperties
import org.springframework.core.annotation.Order
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/** 기기 식별자는 IdP 왕복을 견디지 못한다. 로그인 시작 요청에서 받아 서버에 남겨야 한다. */
class OAuth2DeviceIdFilterTest : BehaviorSpec({

    val filter = OAuth2DeviceIdFilter()

    fun run(req: MockHttpServletRequest) = filter.doFilter(req, MockHttpServletResponse(), MockFilterChain())

    Given("로그인 시작 요청에 deviceId 쿼리 파라미터가 실리면") {
        val req = MockHttpServletRequest("GET", "/oauth2/authorization/google")
            .apply { addParameter("deviceId", "device-B") }

        run(req)

        Then("세션에 보관한다") {
            req.session!!.getAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE) shouldBe "device-B"
        }
    }

    Given("로그인 시작 요청이 헤더로 기기 id 를 보내면") {
        val req = MockHttpServletRequest("GET", "/oauth2/authorization/apple")
            .apply { addHeader("X-Device-Id", "device-C") }

        run(req)

        Then("세션에 보관한다") {
            req.session!!.getAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE) shouldBe "device-C"
        }
    }

    Given("기기 id 가 빈 문자열이면") {
        val req = MockHttpServletRequest("GET", "/oauth2/authorization/google")
            .apply { addParameter("deviceId", "  ") }

        run(req)

        Then("보관하지 않는다") {
            // 빈 값을 넣으면 바인딩이 공백 문자열로 잡혀 1인 1기기 판정이 무의미해진다.
            req.getSession(false)?.getAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE) shouldBe null
        }
    }

    Given("로그인 시작 경로가 아닌 요청이면") {
        val req = MockHttpServletRequest("POST", "/api/v1/auth/refresh")
            .apply { addHeader("X-Device-Id", "device-D") }

        run(req)

        Then("세션을 만들지 않는다") {
            // 모든 요청마다 세션을 만들면 STATELESS 를 깨고 세션 저장소만 채운다.
            req.getSession(false) shouldBe null
        }
    }

    Given("필터 등록 순서") {
        Then("시큐리티 필터 체인보다 먼저 돈다") {
            // 뒤로 밀리면 OAuth2AuthorizationRequestRedirectFilter 가 리다이렉트로 요청을 끝내버려
            // 이 필터가 아예 실행되지 않는다. 조용히 안 도는 종류라 순서를 못 박아 둔다.
            val order = OAuth2DeviceIdFilter::class.java.getAnnotation(Order::class.java).value

            (order < SecurityProperties.DEFAULT_FILTER_ORDER) shouldBe true
        }
    }
})
