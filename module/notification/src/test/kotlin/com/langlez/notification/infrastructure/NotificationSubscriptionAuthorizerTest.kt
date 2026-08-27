package com.langlez.notification.infrastructure

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 알림 토픽엔 인가 인터셉터가 아예 없었다. 로그인만 하면 남의 알림(발신자 id·방 id·미리보기)을
 * 실시간으로 받아볼 수 있었다.
 */
class NotificationSubscriptionAuthorizerTest : BehaviorSpec({

    val authorizer = NotificationSubscriptionAuthorizer()

    Given("알림 토픽이면") {

        Then("자기 것이라 판정한다") {
            authorizer.supports("/topic/notification/1") shouldBe true
        }

        When("본인 알림 토픽이면") {
            Then("통과한다") {
                authorizer.authorize("/topic/notification/1", 1L) shouldBe true
            }
        }

        When("다른 회원의 알림 토픽이면") {
            Then("거부한다") {
                authorizer.authorize("/topic/notification/2", 1L) shouldBe false
            }
        }
    }

    Given("알림 토픽 모양이 아니면") {
        Then("자기 것이 아니라고 판정한다") {
            // 숫자만 허용한다. 별표를 끼우면 심플 브로커가 전체 회원의 알림을 밀어준다.
            authorizer.supports("/topic/notification/*") shouldBe false
            authorizer.supports("/topic/notification/1/extra") shouldBe false
            authorizer.supports("/topic/chat/room/1") shouldBe false
        }
    }
})
