package com.langlez.config

import com.langlez.core.SubscriptionAuthorizer
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.ObjectProvider
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

/**
 * 기본 거부. 인가자가 손들지 않은 목적지는 전부 막는다 —
 * 새 모듈이 토픽을 추가하면서 인가를 빠뜨려도 열리지 않아야 한다.
 */
class WebSocketSubscriptionGateTest : BehaviorSpec({

    val channel = mockk<MessageChannel>()

    fun gate(vararg authorizers: SubscriptionAuthorizer): WebSocketSubscriptionGate {
        val provider = mockk<ObjectProvider<SubscriptionAuthorizer>>()
        every { provider.iterator() } answers { authorizers.toMutableList().iterator() }

        return WebSocketSubscriptionGate(provider)
    }

    fun authorizerOf(prefix: String, allow: Boolean) = object : SubscriptionAuthorizer {
        override fun supports(destination: String) = destination.startsWith(prefix)
        override fun authorize(destination: String, memberId: Long) = allow
    }

    fun frame(command: StompCommand, destination: String?, memberId: Long?): Message<*> {
        val accessor = StompHeaderAccessor.create(command)
        destination?.let { accessor.destination = it }
        memberId?.let { accessor.user = UsernamePasswordAuthenticationToken(it, null, emptyList()) }

        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    Given("SUBSCRIBE 목적지를 아무 인가자도 자기 것이라 하지 않으면") {

        Then("거부한다") {
            val subject = gate(authorizerOf("/topic/known/", allow = true))

            shouldThrow<IllegalArgumentException> { subject.preSend(frame(StompCommand.SUBSCRIBE, "/topic/**", 1L), channel) }
            shouldThrow<IllegalArgumentException> {
                subject.preSend(frame(StompCommand.SUBSCRIBE, "/topic/whatever", 1L), channel)
            }
        }

        Then("인가자가 하나도 등록되지 않았어도 거부한다") {
            shouldThrow<IllegalArgumentException> {
                gate().preSend(frame(StompCommand.SUBSCRIBE, "/topic/known/1", 1L), channel)
            }
        }
    }

    Given("인가자가 자기 것이라 판정한 목적지면") {

        When("인가자가 허용하면") {
            Then("통과한다") {
                shouldNotThrowAny {
                    gate(authorizerOf("/topic/known/", allow = true))
                        .preSend(frame(StompCommand.SUBSCRIBE, "/topic/known/1", 1L), channel)
                }
            }
        }

        When("인가자가 거부하면") {
            Then("거부한다") {
                shouldThrow<IllegalArgumentException> {
                    gate(authorizerOf("/topic/known/", allow = false))
                        .preSend(frame(StompCommand.SUBSCRIBE, "/topic/known/1", 1L), channel)
                }
            }
        }

        When("인증 주체가 없으면") {
            Then("거부한다") {
                shouldThrow<IllegalArgumentException> {
                    gate(authorizerOf("/topic/known/", allow = true))
                        .preSend(frame(StompCommand.SUBSCRIBE, "/topic/known/1", null), channel)
                }
            }
        }
    }

    Given("SUBSCRIBE 가 아닌 프레임이면") {
        Then("건드리지 않는다") {
            // CONNECT 인증은 chat 인터셉터 몫이다. 여기서 막으면 연결 자체가 안 된다.
            shouldNotThrowAny {
                gate().preSend(frame(StompCommand.CONNECT, null, null), channel)
            }
        }
    }
})
