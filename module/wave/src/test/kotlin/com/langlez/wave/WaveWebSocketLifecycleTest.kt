package com.langlez.wave

import com.langlez.core.MessageBroadcaster
import com.langlez.wave.application.WaveService
import com.langlez.wave.domain.WaveRepository
import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.domain.WaveSessionRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.messaging.Message
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.messaging.SessionSubscribeEvent

/**
 * 소켓이 끊길 때의 방 정리.
 *
 * 앱을 강제 종료하면 `DELETE .../participants/me` 가 오지 않는다. 그 경로가 없으면 참여자 집합에
 * 유령이 남아 정원을 잡아먹고, 전원이 그렇게 사라진 방은 `ended_at` 이 NULL 로 굳어 목록에 영구 상주한다.
 */
class WaveWebSocketLifecycleTest : BehaviorSpec({

    val repo = mockk<WaveRepository>()
    val sessions = mockk<WaveSessionRepository>(relaxed = true)
    val service = WaveService(repo, sessions, mockk<MessageBroadcaster>(relaxed = true))

    val config = WaveWebSocketConfiguration(service)
    val subscribeListener = config.waveRoomSubscribeListener()
    val disconnectListener = config.waveRoomDisconnectListener()

    afterEach { clearMocks(repo, sessions, answers = false) }

    val memberId = 7L
    val principal = UsernamePasswordAuthenticationToken(memberId, null, listOf(SimpleGrantedAuthority("ROLE_USER")))

    fun message(command: StompCommand, attributes: MutableMap<String, Any>, destination: String? = null): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(command)
        accessor.sessionId = "session-1"
        accessor.sessionAttributes = attributes
        destination?.let { accessor.destination = it }

        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    fun subscribe(attributes: MutableMap<String, Any>, destination: String) =
        subscribeListener.onApplicationEvent(
            SessionSubscribeEvent(this, message(StompCommand.SUBSCRIBE, attributes, destination), principal)
        )

    fun disconnect(attributes: MutableMap<String, Any>, user: UsernamePasswordAuthenticationToken? = principal) =
        disconnectListener.onApplicationEvent(
            SessionDisconnectEvent(
                this,
                message(StompCommand.DISCONNECT, attributes),
                "session-1",
                CloseStatus.NORMAL,
                user,
            )
        )

    Given("방 토픽을 구독하면") {

        When("목적지가 wave 방이면") {
            Then("이 세션이 듣는 방으로 기록한다") {
                val attributes = mutableMapOf<String, Any>()

                subscribe(attributes, "/topic/wave/42/chat")

                attributes.values shouldBe listOf(42L)
            }
        }

        When("남의 모듈 토픽이면") {
            Then("아무것도 남기지 않는다") {
                val attributes = mutableMapOf<String, Any>()

                subscribe(attributes, "/topic/chat/room/42")

                attributes.shouldBeEmpty()
            }
        }
    }

    Given("소켓이 끊기면") {

        When("듣던 방이 있고 아직 남은 사람이 있으면") {
            Then("그 방에서만 빠지고 방은 열어 둔다") {
                val attributes = mutableMapOf<String, Any>()
                subscribe(attributes, "/topic/wave/42/chat")
                every { sessions.participants(42L) } returns setOf(9L)

                disconnect(attributes)

                verify { sessions.leave(42L, memberId) }
                verify(exactly = 0) { repo.save(any()) }
            }
        }

        When("마지막 참여자가 그렇게 사라지면") {
            Then("ended_at 을 채워 죽은 방이 목록에 남지 않게 한다") {
                val room = WaveRoom(id = 42L, broadcasterId = memberId, title = "영어 수다방")
                val attributes = mutableMapOf<String, Any>()
                subscribe(attributes, "/topic/wave/42/chat")

                every { sessions.participants(42L) } returns emptySet()
                every { repo.find(42L) } returns room
                every { repo.save(any()) } answers { firstArg() }

                disconnect(attributes)

                room.isEnded() shouldBe true
                verify { sessions.clear(42L) }
            }
        }

        When("여러 방을 듣고 있었고 한 방 정리가 실패하면") {
            Then("나머지 방은 그대로 정리한다") {
                val attributes = mutableMapOf<String, Any>()
                subscribe(attributes, "/topic/wave/42/chat")
                subscribe(attributes, "/topic/wave/43/chat")

                every { sessions.leave(42L, memberId) } throws IllegalStateException("레디스 순단")
                every { sessions.participants(43L) } returns setOf(9L)

                disconnect(attributes)

                verify { sessions.leave(43L, memberId) }
            }
        }

        When("인증 전에 끊긴 세션이면") {
            Then("아무 방도 건드리지 않는다") {
                val attributes = mutableMapOf<String, Any>()
                subscribe(attributes, "/topic/wave/42/chat")

                disconnect(attributes, user = null)

                verify(exactly = 0) { sessions.leave(any(), any()) }
            }
        }

        When("듣던 방이 없으면") {
            Then("아무 일도 하지 않는다") {
                disconnect(mutableMapOf())

                verify(exactly = 0) { sessions.leave(any(), any()) }
            }
        }
    }
})
