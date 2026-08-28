package com.langlez.chat

import com.langlez.chat.domain.ChatRepository
import com.langlez.chat.infrastructure.ChatSubscriptionAuthorizer
import com.langlez.config.WebSocketSubscriptionGate
import com.langlez.core.OnlineTracker
import com.langlez.core.SubscriptionAuthorizer
import com.langlez.core.TokenBlacklist
import com.langlez.utility.JwtTokenProvider
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.beans.factory.ObjectProvider
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

/** `getInterceptors()` 가 protected 라 하위 클래스로만 꺼낼 수 있다. */
private class ExposedRegistration : ChannelRegistration() {
    fun all(): List<ChannelInterceptor> = interceptors ?: emptyList()
}

class ChatWebSocketSubscriptionTest : BehaviorSpec({

    val repo = mockk<ChatRepository>()
    val tracker = mockk<OnlineTracker>(relaxed = true)
    val channel = mockk<MessageChannel>()

    // 게이트는 등록된 인가자를 ObjectProvider 로 받는다. chat 컨텍스트엔 chat 인가자만 있다.
    val authorizers = mockk<ObjectProvider<SubscriptionAuthorizer>>().also {
        every { it.iterator() } answers { mutableListOf<SubscriptionAuthorizer>(ChatSubscriptionAuthorizer(repo)).iterator() }
    }

    fun chain(): List<ChannelInterceptor> {
        val configuration = ChatWebSocketConfiguration(
            jwt = mockk<JwtTokenProvider>(),
            tokenBlacklist = mockk<TokenBlacklist>(),
            tracker = tracker,
            gate = WebSocketSubscriptionGate(authorizers),
        )
        val registration = ExposedRegistration()
        configuration.configureClientInboundChannel(registration)
        return registration.all()
    }

    fun subscribe(destination: String, memberId: Long) {
        val accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        accessor.destination = destination
        accessor.subscriptionId = "sub-1"
        accessor.sessionAttributes = mutableMapOf()
        accessor.user = UsernamePasswordAuthenticationToken(memberId, null, emptyList())

        var message: Message<*> = MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
        chain().forEach { message = it.preSend(message, channel) ?: return }
    }

    Given("어느 모듈도 자기 것이라 주장하지 않는 목적지를 구독하면") {

        When("브로커 전체를 와일드카드로 구독하면") {
            Then("거부한다") {
                shouldThrow<IllegalArgumentException> { subscribe("/topic/**", 1L) }
            }
        }

        When("아는 패턴이 아닌 임의 목적지를 구독하면") {
            Then("거부한다") {
                shouldThrow<IllegalArgumentException> { subscribe("/topic/whatever", 1L) }
            }
        }

        When("다른 회원의 알림 토픽을 구독하면") {
            Then("거부한다") {
                // chat 컨텍스트엔 notification 인가자가 없다. 모르는 목적지는 기본 거부다.
                shouldThrow<IllegalArgumentException> { subscribe("/topic/notification/999", 1L) }
            }
        }
    }

    Given("채팅방 토픽을 구독하면") {

        When("그 방의 참여자면") {
            Then("통과하고 보는 중으로 기록된다") {
                every { repo.findParticipant(7L, 1L) } returns mockk()

                shouldNotThrowAny { subscribe("/topic/chat/room/7", 1L) }
                verify { tracker.recordViewing(1L, "/topic/chat/room/7") }
            }
        }

        When("참여자가 아니면") {
            Then("거부하고 보는 중으로 기록하지 않는다") {
                every { repo.findParticipant(8L, 2L) } returns null

                shouldThrow<IllegalArgumentException> { subscribe("/topic/chat/room/8", 2L) }
                verify(exactly = 0) { tracker.recordViewing(2L, any()) }
            }
        }
    }
})
