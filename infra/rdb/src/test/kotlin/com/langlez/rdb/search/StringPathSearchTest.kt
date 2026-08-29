package com.langlez.rdb.search

import com.querydsl.core.types.dsl.Expressions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe

class StringPathSearchTest : BehaviorSpec({

    val path = Expressions.stringPath("content")

    Given("검색어 길이 경계값") {
        When("1자로 검색하면") {
            Then("IllegalArgumentException 이 발생하고 메시지는 i18n 키다") {
                val ex = shouldThrow<IllegalArgumentException> { path.search("a") }
                ex.message shouldBe "validation.search.min-length"
            }
        }

        When("2자로 검색하면") {
            Then("예외 없이 식이 만들어진다") {
                path.search("ab")
            }
        }
    }

    Given("공백이 섞인 검색어") {
        When("앞뒤 공백만 있는 2자 입력이면") {
            Then("trim 후 2자로 통과한다") {
                path.search("  ab  ")
            }
        }

        When("trim 후 1자만 남으면") {
            Then("IllegalArgumentException 이 발생한다") {
                shouldThrow<IllegalArgumentException> { path.search("  a  ") }
            }
        }
    }

    Given("와일드카드 문자가 든 검색어") {
        When("%, _ 가 검색어에 있으면") {
            Then("리터럴로 이스케이프되어 식에 담긴다") {
                val expr = path.search("100%_off").toString()
                expr shouldContain "100\\%\\_off"
            }
        }

        When("백슬래시가 검색어에 있으면") {
            Then("백슬래시부터 먼저 이스케이프된다") {
                val expr = path.search("a\\b").toString()
                expr shouldContain "a\\\\b"
            }
        }
    }

    Given("합성") {
        When("and/or/not 으로 묶으면") {
            Then("예외 없이 합성된 BooleanExpression 이 만들어진다") {
                val combined = path.search("ab").and(path.search("cd")).or(path.search("ef").not())
                combined shouldBe combined
            }
        }
    }
})
