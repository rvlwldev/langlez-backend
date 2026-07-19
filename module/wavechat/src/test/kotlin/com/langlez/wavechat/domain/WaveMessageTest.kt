package com.langlez.wavechat.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class WaveMessageTest : BehaviorSpec({

    Given("WaveMessage가 주어졌을 때") {
        val message = WaveMessage(waveRoomId = 1L, senderId = 10L, content = "hello")

        When("생성 직후") {
            Then("삭제되지 않은 상태여야 한다") {
                message.isDeleted() shouldBe false
                message.deletedAt shouldBe null
            }
        }

        When("delete()를 호출하면") {
            message.delete()

            Then("deletedAt이 설정되고 isDeleted()가 true여야 한다") {
                message.isDeleted() shouldBe true
                message.deletedAt shouldNotBe null
            }
        }

        When("이미 삭제된 상태에서 delete()를 다시 호출하면") {
            val deletedMessage = WaveMessage(waveRoomId = 1L, senderId = 10L, content = "hello")
            deletedMessage.delete()
            val firstDeletedAt = deletedMessage.deletedAt

            deletedMessage.delete()

            Then("deletedAt이 그대로 유지되어야 한다(idempotent)") {
                deletedMessage.deletedAt shouldBe firstDeletedAt
            }
        }
    }
})
