package com.langlez.wave.infrastructure

import com.langlez.wave.domain.WaveSessionRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * 구독 인가. 연결 인증만으론 로그인한 아무나 남의 음성방 대화를 실시간으로 엿볼 수 있다.
 *
 * 인터셉터가 `common` 의 `WebSocketSubscriptionGate` 하나로 합쳐지면서, 이 모듈은
 * "내 토픽인가(supports) / 이 회원이 봐도 되는가(authorize)" 판정만 갖는다.
 */
class WaveSubscriptionAuthorizerTest : BehaviorSpec({

    val sessions = mockk<WaveSessionRepository>()
    val authorizer = WaveSubscriptionAuthorizer(sessions)

    Given("wave 방 채팅 토픽이면") {

        Then("자기 것이라 판정한다") {
            authorizer.supports("/topic/wave/7/chat") shouldBe true
        }

        When("그 방의 참여자면") {
            Then("통과한다") {
                every { sessions.isParticipant(7L, 1L) } returns true

                authorizer.authorize("/topic/wave/7/chat", 1L) shouldBe true
            }
        }

        When("참여자가 아니면") {
            Then("거부한다") {
                every { sessions.isParticipant(7L, 2L) } returns false

                authorizer.authorize("/topic/wave/7/chat", 2L) shouldBe false
            }
        }
    }

    Given("wave 토픽이 아니면") {

        When("와일드카드로 전체 방을 구독하려 하면") {
            Then("자기 것이 아니라고 판정한다") {
                // 심플 브로커는 별표 패턴을 지원한다. 느슨하게 열면 모든 방을 한 번에 빨아간다.
                // 판정자가 아무도 손들지 않으면 게이트가 기본 거부한다.
                authorizer.supports("/topic/wave/*/chat") shouldBe false
                authorizer.supports("/topic/wave/7/chat/extra") shouldBe false
            }
        }

        When("다른 모듈의 토픽이면") {
            Then("자기 것이 아니라고 판정한다") {
                // 예전엔 "통과"였다. 이제는 판정을 사양할 뿐이고, 실제 허용 여부는
                // 그 토픽의 인가자가 있느냐로 갈린다 — 없으면 게이트가 막는다.
                authorizer.supports("/topic/chat/room/1") shouldBe false
            }
        }
    }
})
