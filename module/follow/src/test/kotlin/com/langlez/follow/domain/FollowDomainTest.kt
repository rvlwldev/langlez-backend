package com.langlez.follow.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FollowDomainTest : BehaviorSpec({

    Given("자기 자신을 대상으로") {

        When("팔로우를 만들면") {
            Then("i18n 키를 담은 IllegalArgumentException 이 난다") {
                shouldThrow<IllegalArgumentException> { Follow(1L, 1L) }
                    .message shouldBe "social.follow.self"
            }
        }
    }

    Given("남을 대상으로") {

        When("팔로우를 만들면") {
            Then("정상 생성된다") {
                Follow(1L, 2L).followedId shouldBe 2L
            }
        }
    }
})
