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

    /** 컨테이너가 채워 주는 값들을 흉내 낸다. contextPath 가 없으면 servletPath 가 곧 요청 경로다. */
    fun request(path: String = "/oauth2/authorization/google", contextPath: String = "") =
        MockHttpServletRequest("GET", contextPath + path).apply {
            this.contextPath = contextPath
            this.servletPath = path
        }

    fun run(req: MockHttpServletRequest) = filter.doFilter(req, MockHttpServletResponse(), MockFilterChain())

    Given("로그인 시작 요청에 deviceId 쿼리 파라미터가 실리면") {
        val req = request().apply { addParameter("deviceId", "device-B") }

        run(req)

        Then("세션에 보관한다") {
            req.session!!.getAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE) shouldBe "device-B"
        }
    }

    Given("로그인 시작 요청이 헤더로 기기 id 를 보내면") {
        val req = request("/oauth2/authorization/apple").apply { addHeader("X-Device-Id", "device-C") }

        run(req)

        Then("세션에 보관한다") {
            req.session!!.getAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE) shouldBe "device-C"
        }
    }

    Given("context-path 가 붙은 배포라면") {
        // requestURI 로 비교하면 "/api" 가 앞에 붙어 접두사가 어긋나고, 필터가 조용히 아무것도
        // 안 하게 된다. servletPath 는 context-path 를 뺀 경로라 영향을 받지 않는다.
        val req = request(contextPath = "/api").apply { addParameter("deviceId", "device-D") }

        run(req)

        Then("그래도 기기 id 를 보관한다") {
            req.session!!.getAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE) shouldBe "device-D"
        }
    }

    Given("앞선 로그인 시도가 취소돼 세션에 기기 id 가 남아 있으면") {
        When("다음 로그인이 기기 id 를 보내면") {
            val req = request().apply {
                session!!.setAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE, "device-A")
                addParameter("deviceId", "device-B")
            }

            run(req)

            Then("새 값으로 덮인다") {
                req.session!!.getAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE) shouldBe "device-B"
            }
        }

        When("다음 로그인이 기기 id 를 안 보내면") {
            val req = request().apply {
                session!!.setAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE, "device-A")
            }

            run(req)

            Then("남은 값을 지운다") {
                // 안 지우면 취소된 시도의 기기 id 로 이번 세션이 잘못 바인딩된다.
                // SuccessHandler 는 실패·이탈 경로에서 아예 불리지 않으므로 여기서 정리해야 한다.
                req.session!!.getAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE) shouldBe null
            }
        }
    }

    Given("기기 id 가 빈 문자열이면") {
        val req = request().apply { addParameter("deviceId", "  ") }

        run(req)

        Then("보관하지 않는다") {
            // 빈 값을 넣으면 바인딩이 공백 문자열로 잡혀 1인 1기기 판정이 무의미해진다.
            req.getSession(false)?.getAttribute(OAuth2DeviceIdFilter.SESSION_ATTRIBUTE) shouldBe null
        }
    }

    Given("로그인 시작 경로가 아닌 요청이면") {
        val req = request("/api/v1/auth/refresh").apply { addHeader("X-Device-Id", "device-D") }

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
