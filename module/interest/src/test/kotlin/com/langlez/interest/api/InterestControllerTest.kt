package com.langlez.interest.api

import com.langlez.interest.application.InterestService
import com.langlez.interest.application.InterestView
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale

class InterestControllerTest : BehaviorSpec({

    val service = mockk<InterestService>()
    val controller = InterestController(service)
    val locale = Locale.forLanguageTag("ko")

    Given("관심사 검색 요청 시") {
        every { service.search(locale, "등") } returns listOf(InterestView(1, "등산"))

        Then("서비스 결과를 그대로 반환한다") {
            val result = controller.search(locale, "등")
            result.items shouldBe listOf(InterestResponse.Item(1, "등산"))
        }
    }

    Given("내 관심사 설정 요청 시") {
        every { service.setMemberInterests(1L, locale, listOf("등산", "서핑")) } returns Unit

        Then("서비스에 위임한다") {
            controller.setMyInterests(1L, locale, InterestRequest.Set(listOf("등산", "서핑")))
            verify { service.setMemberInterests(1L, locale, listOf("등산", "서핑")) }
        }
    }
})
