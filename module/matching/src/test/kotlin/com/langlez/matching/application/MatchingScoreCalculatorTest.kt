package com.langlez.matching.application

import com.langlez.profile.domain.Profile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class MatchingScoreCalculatorTest : BehaviorSpec({

    Given("baseScore 계산 시") {
        When("BEGINNER 레벨이면") {
            Then("0점이다") {
                MatchingScoreCalculator.baseScore(Profile.LanguageLevel.BEGINNER) shouldBe 0.0
            }
        }
        When("INTERMEDIATE 레벨이면") {
            Then("1000점이다") {
                MatchingScoreCalculator.baseScore(Profile.LanguageLevel.INTERMEDIATE) shouldBe 1000.0
            }
        }
        When("ADVANCED 레벨이면") {
            Then("2000점이다") {
                MatchingScoreCalculator.baseScore(Profile.LanguageLevel.ADVANCED) shouldBe 2000.0
            }
        }
    }

    Given("tolerance(허용 오차) 계산 시") {
        val now = Instant.parse("2026-07-19T00:00:00Z")

        When("방금 큐에 참가했다면(대기 0초)") {
            Then("기본 tolerance 200이다") {
                MatchingScoreCalculator.tolerance(now, now) shouldBe 200.0
            }
        }
        When("9초 대기했다면(아직 10초 미만)") {
            Then("여전히 200이다") {
                MatchingScoreCalculator.tolerance(now, now.plusSeconds(9)) shouldBe 200.0
            }
        }
        When("10초 대기했다면") {
            Then("200 + 300 = 500이다") {
                MatchingScoreCalculator.tolerance(now, now.plusSeconds(10)) shouldBe 500.0
            }
        }
        When("35초 대기했다면(3 스텝 경과)") {
            Then("200 + 300*3 = 1100이다") {
                MatchingScoreCalculator.tolerance(now, now.plusSeconds(35)) shouldBe 1100.0
            }
        }
        When("매우 오래(10분) 대기했다면") {
            Then("최대치 2000을 넘지 않는다") {
                MatchingScoreCalculator.tolerance(now, now.plusSeconds(600)) shouldBe 2000.0
            }
        }
    }
})
