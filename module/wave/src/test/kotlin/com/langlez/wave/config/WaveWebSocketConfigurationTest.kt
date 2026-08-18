package com.langlez.wave.config

import com.langlez.wave.domain.WaveSessionRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

/**
 * 구독 인가. 연결 인증만으론 로그인한 아무나 남의 음성방 대화를 실시간으로 엿볼 수 있다.
 */
class WaveWebSocketConfigurationTest : BehaviorSpec({

    val sessions = mockk<WaveSessionRepository>()
    val interceptor = WaveWebSocketConfiguration(sessions).waveSubscriptionInterceptor()

    fun subscribe(destination: String, memberId: Long?): Message<*> {
        val accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        accessor.destination = destination
        memberId?.let { accessor.user = UsernamePasswordAuthenticationToken(it, null, emptyList()) }

        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    val channel = mockk<MessageChannel>()

    Given("wave 방 토픽을 구독하려 하면") {

        When("그 방의 참여자면") {
            Then("통과한다") {
                every { sessions.isParticipant(7L, 1L) } returns true

                shouldNotThrowAny { interceptor.preSend(subscribe("/topic/wave/7/chat", 1L), channel) }
            }
        }

        When("참여자가 아니면") {
            Then("거부한다") {
                every { sessions.isParticipant(7L, 2L) } returns false

                shouldThrow<IllegalArgumentException> {
                    interceptor.preSend(subscribe("/topic/wave/7/chat", 2L), channel)
                }
            }
        }

        When("와일드카드로 전체 방을 구독하려 하면") {
            Then("거부한다") {
                // 심플 브로커는 별표 패턴을 지원한다. 느슨하게 열면 모든 방을 한 번에 빨아간다.
                shouldThrow<IllegalArgumentException> {
                    interceptor.preSend(subscribe("/topic/wave/*/chat", 1L), channel)
                }
            }
        }

        When("인증 주체가 없으면") {
            Then("거부한다") {
                shouldThrow<IllegalArgumentException> {
                    interceptor.preSend(subscribe("/topic/wave/7/chat", null), channel)
                }
            }
        }
    }

    Given("wave 토픽이 아니면") {
        When("다른 모듈의 토픽을 구독하면") {
            Then("건드리지 않고 넘긴다") {
                // chat 인터셉터가 자기 토픽을 따로 검사한다. 여기서 막으면 남의 모듈을 깬다.
                shouldNotThrowAny { interceptor.preSend(subscribe("/topic/chat/room/1", 1L), channel) }
            }
        }
    }
})
