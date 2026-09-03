package com.langlez.lang.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 불변식이 깨지면 매칭이 조용히 틀어진다 — "모국어 초급"이 후보로 잡히거나,
 * 지원하지 않는 언어 코드가 들어와 상호보완 질의에서 영원히 0건이 된다.
 */
class MemberLanguageTest : BehaviorSpec({

    Given("언어 프로필을 만들 때") {

        When("모국어에 레벨을 함께 주면") {
            Then("거부한다") {
                val e = shouldThrow<IllegalArgumentException> {
                    MemberLanguage(
                        memberId = 1L,
                        language = "ko",
                        role = MemberLanguage.Role.NATIVE,
                        level = MemberLanguage.Level.BEGINNER,
                    )
                }
                e.message shouldBe "lang.level.invalid"
            }
        }

        When("학습언어에 레벨을 주지 않으면") {
            Then("거부한다") {
                val e = shouldThrow<IllegalArgumentException> {
                    MemberLanguage(memberId = 1L, language = "en", role = MemberLanguage.Role.LEARNING)
                }
                e.message shouldBe "lang.level.invalid"
            }
        }

        When("지원하지 않는 언어 코드를 주면") {
            Then("거부한다") {
                val e = shouldThrow<IllegalArgumentException> {
                    MemberLanguage(memberId = 1L, language = "kr", role = MemberLanguage.Role.NATIVE)
                }
                e.message shouldBe "lang.unsupported"
            }
        }

        When("i18n 파일명 표기(zh_CN)를 그대로 주면") {
            Then("거부한다. 지원 목록은 BCP-47 표기(zh-CN)다") {
                shouldThrow<IllegalArgumentException> {
                    MemberLanguage(memberId = 1L, language = "zh_CN", role = MemberLanguage.Role.NATIVE)
                }
            }
        }

        When("모국어를 레벨 없이 주면") {
            Then("만들어진다") {
                MemberLanguage(memberId = 1L, language = "zh-CN", role = MemberLanguage.Role.NATIVE).level shouldBe null
            }
        }
    }

    Given("레벨 상수는") {
        Then("BEGINNER < INTERMEDIATE < ADVANCED 순서다") {
            // 매칭의 레벨 근접도가 이 ordinal 차이로 계산된다. 순서가 바뀌면 점수만 조용히 틀어진다.
            MemberLanguage.Level.entries.map { it.name } shouldBe listOf("BEGINNER", "INTERMEDIATE", "ADVANCED")
        }
    }
})
