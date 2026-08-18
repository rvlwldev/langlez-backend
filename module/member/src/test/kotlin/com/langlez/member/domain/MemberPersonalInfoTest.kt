package com.langlez.member.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.Locale

/**
 * 생년월일/성별/국가 같은 개인식별 정보는 프로필(자기소개 성격)이 아니라 계정에 속한다.
 * 프로필이 없는 회원도 이 값들을 가질 수 있어야 한다.
 */
class MemberPersonalInfoTest : BehaviorSpec({

    fun member() = Member(
        email = "user@test.com",
        provider = Member.Provider.GOOGLE,
        providerId = "p1",
    )

    Given("회원을 새로 만들면") {
        val target = member()

        Then("성별은 SECRET 이 기본값이다") {
            target.gender shouldBe Member.Gender.SECRET
        }

        Then("생년월일은 비어 있다") {
            target.birthDay shouldBe null
        }
    }

    Given("개인정보를 채우면") {
        val target = member().apply {
            gender = Member.Gender.FEMALE
            birthDay = LocalDate.of(1995, 3, 14)
            locale = Locale.KOREA
        }

        Then("성별과 생년월일이 그대로 유지된다") {
            target.gender shouldBe Member.Gender.FEMALE
            target.birthDay shouldBe LocalDate.of(1995, 3, 14)
        }

        Then("locale 로 넣은 국가는 country 에 저장된다") {
            target.country shouldBe "KR"
        }

        Then("country 로 넣으면 locale 로 읽힌다") {
            member().apply { country = "US" }.locale?.country shouldBe "US"
        }
    }
})
