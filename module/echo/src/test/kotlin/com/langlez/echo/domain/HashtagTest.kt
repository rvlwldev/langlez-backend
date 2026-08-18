package com.langlez.echo.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize

class HashtagTest : BehaviorSpec({

    Given("본문에서 해시태그를 뽑을 때") {

        When("영문·숫자·한글 태그가 섞여 있으면") {
            Then("모두 뽑고 대소문자는 소문자로 모은다") {
                Hashtag.extract("#Seoul 에서 #커피2 마시는 중 #seoul") shouldContainExactly setOf("seoul", "커피2")
            }
        }

        When("태그가 없으면") {
            Then("빈 집합이다") {
                Hashtag.extract("그냥 평범한 글 # 과 ##").shouldBeEmpty()
            }
        }

        When("태그가 상한을 넘으면") {
            Then("앞에서부터 상한만큼만 남긴다") {
                val content = (1..20).joinToString(" ") { "#tag$it" }
                Hashtag.extract(content) shouldHaveSize Hashtag.MAX_PER_POST
            }
        }
    }
})
