package com.langlez.chat

import com.langlez.config.WebSocketSubscriptionGate
import com.langlez.core.SubscriptionAuthorizer
import com.langlez.member.contract.MemberReader
import com.langlez.member.contract.MemberReader.Status
import com.langlez.member.contract.OnlineTracker
import com.langlez.security.TokenManager
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
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
import java.util.Base64

/** `getInterceptors()` 가 protected 라 하위 클래스로만 꺼낼 수 있다. */
private class ExposedConnectRegistration : ChannelRegistration() {
    fun all(): List<ChannelInterceptor> = interceptors ?: emptyList()
}

/**
 * CONNECT 시점 계정 상태 검사.
 *
 * 소켓은 한 번 열리면 재검증 지점이 없어서, 여기서 못 막으면 정지된 회원이 액세스 토큰
 * TTL 내내 다시 붙어 상대 메시지를 읽는다. HTTP(`JwtAuthenticationFilter`)와 같은 판정표를
 * 쓰는지까지 여기서 본다.
 */
class ChatWebSocketConnectTest : BehaviorSpec({

    val members = mockk<MemberReader>()
    val tracker = mockk<OnlineTracker>(relaxed = true)
    val sessions = mockk<WebSocketSessionRegistry>(relaxed = true)
    val channel = mockk<MessageChannel>()

    val secret = Base64.getEncoder().encodeToString("super-secret-key-12345678901234567890".toByteArray())
    val tokens = TokenManager(secret, accessTokenTTL = 3600, refreshTokenTTL = 86400, redisson = mockk(relaxed = true))

    // CONNECT 만 보는 스펙이라 구독 인가자는 필요 없다. 게이트는 SUBSCRIBE 가 아니면 통과시킨다.
    val authorizers = mockk<ObjectProvider<SubscriptionAuthorizer>>().also {
        every { it.iterator() } answers { mutableListOf<SubscriptionAuthorizer>().iterator() }
    }

    fun chain(): List<ChannelInterceptor> {
        val configuration = ChatWebSocketConfiguration(
            tokens = tokens,
            members = members,
            tracker = tracker,
            gate = WebSocketSubscriptionGate(authorizers),
            sessions = sessions,
        )
        val registration = ExposedConnectRegistration()
        configuration.configureClientInboundChannel(registration)
        return registration.all()
    }

    fun connect(memberId: Long) {
        val accessor = StompHeaderAccessor.create(StompCommand.CONNECT)
        // 인터셉터가 accessor.user 를 심는다. 기본값은 헤더가 굳어 "Already immutable" 로 터진다 —
        // 운영에서는 StompSubProtocolHandler 가 CONNECT 프레임을 mutable 로 넘긴다.
        accessor.setLeaveMutable(true)
        accessor.sessionId = "sess-$memberId"
        accessor.sessionAttributes = mutableMapOf()
        accessor.setNativeHeader("Authorization", "Bearer ${tokens.issueAccessToken(memberId, "user$memberId", "USER")}")

        var message: Message<*> = MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
        chain().forEach { message = it.preSend(message, channel) ?: return }
    }

    Given("CONNECT 프레임의 계정 상태별로") {

        // Member.verify() 호출자가 아직 0건이라 실사용 회원이 전부 CREATED 다.
        // 여기서 막으면 신규 가입자가 아니라 전원이 실시간 채널에서 잘린다.
        When("CREATED 회원이면") {
            every { members.findStatus(1L) } returns Status.CREATED

            Then("통과한다") {
                shouldNotThrowAny { connect(1L) }
            }
        }

        When("ACTIVE 회원이면") {
            every { members.findStatus(2L) } returns Status.ACTIVE

            Then("통과한다") {
                shouldNotThrowAny { connect(2L) }
            }
        }

        When("SUSPENDED 회원이면") {
            every { members.findStatus(3L) } returns Status.SUSPENDED

            Then("member.suspended 로 거부한다") {
                val ex = shouldThrow<IllegalArgumentException> { connect(3L) }
                ex.message shouldBe "member.suspended"
            }
        }

        When("WITHDRAWN 회원이면") {
            every { members.findStatus(4L) } returns Status.WITHDRAWN

            Then("member.withdrawn 으로 거부한다") {
                val ex = shouldThrow<IllegalArgumentException> { connect(4L) }
                ex.message shouldBe "member.withdrawn"
            }
        }

        When("회원 행이 아예 없으면") {
            every { members.findStatus(5L) } returns null

            Then("토큰을 무효로 보고 거부한다") {
                val ex = shouldThrow<IllegalArgumentException> { connect(5L) }
                ex.message shouldBe "auth.invalid-token"
            }
        }
    }

    Given("상태 조회 자체가 실패하면") {

        When("포트가 예외를 던지면") {
            every { members.findStatus(6L) } throws IllegalStateException("db down")

            // 보안 판정이라 조회 실패는 통과가 아니라 거부다. MessageDeduplicator 의
            // fail-open 과 정반대 경우다 — 여기서 흘리면 정지 회원이 그대로 붙는다.
            Then("연결이 거부된다") {
                shouldThrow<IllegalStateException> { connect(6L) }
            }

            Then("세션 주인으로 결속되지 않는다") {
                verify(exactly = 0) { sessions.bind("sess-6", 6L) }
            }
        }
    }

    Given("통과한 CONNECT 는") {

        When("인증이 끝나면") {
            every { members.findStatus(7L) } returns Status.ACTIVE

            connect(7L)

            // 결속이 없으면 정지 조치가 그 세션을 찾지 못해 끊지 못한다.
            Then("세션에 회원이 결속된다") {
                verify { sessions.bind("sess-7", 7L) }
            }
        }
    }
})
