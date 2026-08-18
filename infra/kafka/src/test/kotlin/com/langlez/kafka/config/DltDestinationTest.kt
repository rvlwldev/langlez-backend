package com.langlez.kafka.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerRecord

class DltDestinationTest : BehaviorSpec({

    Given("소스 토픽의 3번 파티션에서 온 레코드를 DLT 로 보낼 때") {
        val record = ConsumerRecord("orders", 3, 0L, "k", "v")
        val destination = KafkaConfiguration.dltDestination(record, RuntimeException("boom"))

        Then("토픽은 .DLT 접미사가 붙는다") {
            destination.topic() shouldBe "orders.DLT"
        }

        Then("파티션은 -1 이라 프로듀서가 알아서 분배한다") {
            // 소스 파티션 번호를 그대로 쓰면 DLT 파티션 수가 더 적을 때 발행이 실패하고,
            // 실패한 레코드를 다시 seek 해서 무한 재시도에 빠진다.
            destination.partition() shouldBe -1
        }
    }
})
