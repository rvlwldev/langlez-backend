package com.langlez.member.domain

import io.kotest.assertions.throwables.shouldThrow
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

    Given("닉네임을 새로 만들면") {
        Then("null 이 기본값이다") {
            member().nickname shouldBe null
        }
    }

    Given("닉네임을 바꾸면") {
        When("정상 범위의 닉네임이면") {
            val target = member().apply { changeNickname("지수") }

            Then("그대로 저장된다") {
                target.nickname shouldBe "지수"
            }
        }

        When("앞뒤 공백이 섞여 있으면") {
            val target = member().apply { changeNickname("  지수  ") }

            Then("trim 되어 저장된다") {
                target.nickname shouldBe "지수"
            }
        }

        When("최대 길이(${Member.NICKNAME_MAX_LENGTH}자) 그대로면") {
            val exact = "a".repeat(Member.NICKNAME_MAX_LENGTH)
            val target = member().apply { changeNickname(exact) }

            Then("저장된다") {
                target.nickname shouldBe exact
            }
        }

        When("최대 길이를 1 초과하면") {
            Then("IllegalArgumentException(member.nickname.invalid) 이 발생한다") {
                val ex = shouldThrow<IllegalArgumentException> {
                    member().changeNickname("a".repeat(Member.NICKNAME_MAX_LENGTH + 1))
                }
                ex.message shouldBe "member.nickname.invalid"
            }
        }

        When("공백만 있으면") {
            Then("IllegalArgumentException(member.nickname.invalid) 이 발생한다") {
                val ex = shouldThrow<IllegalArgumentException> {
                    member().changeNickname("   ")
                }
                ex.message shouldBe "member.nickname.invalid"
            }
        }

        When("빈 문자열이면") {
            Then("IllegalArgumentException(member.nickname.invalid) 이 발생한다") {
                val ex = shouldThrow<IllegalArgumentException> {
                    member().changeNickname("")
                }
                ex.message shouldBe "member.nickname.invalid"
            }
        }

        listOf(
            "한국어닉네임",
            "にほんごニックネーム",
            "中文昵称",
            "Кириллица",
            "Émile Zøe",
        ).forEach { nickname ->
            When("$nickname 처럼 다국어 문자면") {
                val target = member().apply { changeNickname(nickname) }

                Then("문자 종류 제한 없이 그대로 저장된다") {
                    target.nickname shouldBe nickname
                }
            }
        }
    }
})
