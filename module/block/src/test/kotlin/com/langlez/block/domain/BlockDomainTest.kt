package com.langlez.block.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class BlockDomainTest : BehaviorSpec({

    Given("자기 자신을 대상으로") {

        When("차단을 만들면") {
            Then("i18n 키를 담은 IllegalArgumentException 이 난다") {
                shouldThrow<IllegalArgumentException> { Block(1L, 1L) }
                    .message shouldBe "social.block.self"
            }
        }
    }

    Given("남을 대상으로") {

        When("차단을 만들면") {
            Then("정상 생성된다") {
                Block(1L, 2L).blockedId shouldBe 2L
            }
        }
    }
})
