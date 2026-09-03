package com.langlez.matching.application

import com.langlez.lang.contract.LanguageReader.LanguageInfo
import com.langlez.lang.contract.LanguageReader.Level
import com.langlez.lang.contract.LanguageReader.Role
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 랭킹이 틀려도 컴파일과 통합테스트는 전부 통과한다. 화면만 보고는 못 찾는 종류라 여기서 고정한다.
 */
class MatchScorerTest : BehaviorSpec({

    val scorer = MatchScorer()

    fun native(language: String) = LanguageInfo(language, Role.NATIVE, null)
    fun learning(language: String, level: Level) = LanguageInfo(language, Role.LEARNING, level)

    // 한국어 모국어 / 영어를 INTERMEDIATE 로 배운다
    val mine = listOf(native("ko"), learning("en", Level.INTERMEDIATE))

    Given("상대가 내 학습언어를 모국어로 하면") {
        val theirs = listOf(native("en"), learning("ko", Level.INTERMEDIATE))

        Then("그 언어가 상호보완 쌍으로 잡힌다") {
            scorer.matchedPairs(mine, theirs) shouldBe
                listOf(MatchScorer.MatchedPair(myLearning = "en", theirNative = "en"))
        }

        Then("쌍 하나당 10점이다") {
            scorer.score(mine, theirs, online = false) shouldBe 10 + 2
        }

        Then("접속 중이면 5점이 붙는다") {
            scorer.score(mine, theirs, online = true) shouldBe 10 + 5 + 2
        }
    }

    Given("상대가 내 학습언어를 모국어로 하지 않으면") {
        val theirs = listOf(native("ja"), learning("ko", Level.BEGINNER))

        Then("상호보완 쌍이 없다") {
            scorer.matchedPairs(mine, theirs) shouldBe emptyList()
        }
    }

    Given("레벨 근접도는 상호 학습자 기준이다") {

        When("서로의 학습 레벨이 같으면") {
            val theirs = listOf(native("en"), learning("ko", Level.INTERMEDIATE))

            Then("2점이다") {
                scorer.levelProximity(mine, theirs) shouldBe 2
            }
        }

        When("한 단계 차이면") {
            val theirs = listOf(native("en"), learning("ko", Level.ADVANCED))

            Then("1점이다") {
                scorer.levelProximity(mine, theirs) shouldBe 1
            }
        }

        When("두 단계 차이면") {
            val advanced = listOf(native("ko"), learning("en", Level.ADVANCED))
            val theirs = listOf(native("en"), learning("ko", Level.BEGINNER))

            Then("0점이다") {
                scorer.levelProximity(advanced, theirs) shouldBe 0
            }
        }

        When("상대가 내 모국어를 배우지 않으면") {
            val theirs = listOf(native("en"), learning("ja", Level.INTERMEDIATE))

            Then("계산할 쌍이 없어 0점이다 — 예외가 아니다") {
                scorer.levelProximity(mine, theirs) shouldBe 0
            }
        }

        When("모국어가 여러 개라 쌍이 여러 개면") {
            val multi = listOf(
                native("ko"), native("ja"),
                learning("en", Level.BEGINNER), learning("fr", Level.ADVANCED),
            )
            // 상대는 en·fr 모국어이고, 내 모국어 중 ja 를 BEGINNER 로 배운다.
            // (fr ADVANCED, ja BEGINNER) 는 gap 2 지만 (en BEGINNER, ja BEGINNER) 는 gap 0 이다.
            val theirs = listOf(native("en"), native("fr"), learning("ja", Level.BEGINNER))

            Then("가장 가까운 쌍 하나를 쓴다") {
                scorer.levelProximity(multi, theirs) shouldBe 2
            }
        }
    }

    Given("점수가 같은 후보가 여럿이면") {
        val theirs = listOf(native("en"), learning("ko", Level.INTERMEDIATE))
        val candidates = mapOf(30L to theirs, 10L to theirs, 20L to theirs)

        Then("id 오름차순으로 깬다 — 순서가 흔들리면 offset 페이징이 항목을 중복시킨다") {
            scorer.rank(mine, candidates, online = emptyMap()) shouldBe listOf(10L, 20L, 30L)
        }

        Then("같은 입력이면 몇 번을 불러도 같은 순서다") {
            val first = scorer.rank(mine, candidates, online = emptyMap())
            scorer.rank(mine, candidates, online = emptyMap()) shouldBe first
        }
    }

    Given("점수가 다르면") {
        val strong = listOf(native("en"), learning("ko", Level.INTERMEDIATE))
        val weak = listOf(native("ja"), learning("ko", Level.INTERMEDIATE))
        val candidates = mapOf(10L to weak, 99L to strong)

        Then("id 가 커도 점수가 높은 쪽이 앞이다") {
            scorer.rank(mine, candidates, online = emptyMap()) shouldBe listOf(99L, 10L)
        }

        Then("상호보완 쌍 하나(10점)는 접속(5점)+레벨(2점)을 합쳐도 못 뒤집는다") {
            scorer.rank(mine, candidates, online = mapOf(10L to true)) shouldBe listOf(99L, 10L)
        }
    }
})
