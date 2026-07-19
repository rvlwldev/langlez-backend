package com.langlez.wave.config

import com.langlez.core.LanglezException
import com.langlez.core.TokenBlacklist
import com.langlez.security.util.JwtParser
import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.domain.WaveRoomRepository
import com.langlez.wave.infrastructure.WaveViewerTracker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder

class WaveJwtChannelInterceptorTest : BehaviorSpec({

    val jwtParser = mockk<JwtParser>()
    val tokenBlacklist = mockk<TokenBlacklist>()
    val waveRoomRepository = mockk<WaveRoomRepository>()
    val viewerTracker = mockk<WaveViewerTracker>()

    val interceptor = WaveJwtChannelInterceptor(
        jwtParser,
        tokenBlacklist,
        waveRoomRepository,
        viewerTracker
    )

    val channel = mockk<MessageChannel>(relaxed = true)

    afterEach {
        clearMocks(jwtParser, tokenBlacklist, waveRoomRepository, viewerTracker)
    }

    Given("WaveJwtChannelInterceptor SUBSCRIBE 처리 시") {
        val roomId = 1L
        val memberId = 10L
        val destination = "/topic/wave/$roomId/signal"

        fun createSubscribeMessage(destination: String, principalMemberId: Long?): Message<*> {
            val accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
            accessor.destination = destination
            if (principalMemberId != null) {
                accessor.user = WaveStompPrincipal(principalMemberId)
            }
            accessor.setLeaveMutable(true)
            return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
        }

        When("강퇴(밴)된 회원이 방에 진입(SUBSCRIBE)하려 하면") {
            val room = WaveRoom(id = roomId, broadcasterId = 2L, title = "Live Room", maxParticipants = 4)
            every { waveRoomRepository.findById(roomId) } returns room
            every { viewerTracker.isBanned(roomId, memberId) } returns true

            Then("403 예외(wave.banned-user)가 발생하여 입장이 거부된다") {
                val message = createSubscribeMessage(destination, memberId)
                val ex = shouldThrow<LanglezException> {
                    interceptor.preSend(message, channel)
                }
                ex.status shouldBe 403
                ex.message shouldBe "wave.banned-user"
            }
        }

        When("방의 인원이 이미 정원(maxParticipants) 이상인데 새로운 사용자가 진입하려 하면") {
            val room = WaveRoom(id = roomId, broadcasterId = 2L, title = "Full Room", maxParticipants = 4)
            every { waveRoomRepository.findById(roomId) } returns room
            every { viewerTracker.isBanned(roomId, memberId) } returns false
            every { viewerTracker.isViewer(roomId, memberId) } returns false
            every { viewerTracker.viewerCount(roomId) } returns 4L

            Then("403 예외(wave.room-full)가 발생하여 입장이 거부된다") {
                val message = createSubscribeMessage(destination, memberId)
                val ex = shouldThrow<LanglezException> {
                    interceptor.preSend(message, channel)
                }
                ex.status shouldBe 403
                ex.message shouldBe "wave.room-full"
            }
        }

        When("이미 해당 방의 시청자(isViewer=true)인 사용자가 추가 채널을 구독하려 하면") {
            val room = WaveRoom(id = roomId, broadcasterId = 2L, title = "Full Room", maxParticipants = 4)
            every { waveRoomRepository.findById(roomId) } returns room
            every { viewerTracker.isBanned(roomId, memberId) } returns false
            every { viewerTracker.isViewer(roomId, memberId) } returns true
            every { viewerTracker.viewerCount(roomId) } returns 4L
            every { viewerTracker.join(any(), any(), any()) } returns Unit

            Then("거부되지 않고 정상적으로 수락된다") {
                val message = createSubscribeMessage(destination, memberId)
                val result = interceptor.preSend(message, channel)
                result shouldBe message
            }
        }

        When("강퇴(밴)된 회원이 이미 연결된 세션에서 채팅/시그널링(SEND)을 보내려 하면") {
            val room = WaveRoom(id = roomId, broadcasterId = 2L, title = "Live Room", maxParticipants = 4)
            every { waveRoomRepository.findById(roomId) } returns room
            every { viewerTracker.isBanned(roomId, memberId) } returns true

            Then("403 예외(wave.banned-user)가 발생하여 SEND가 거부된다") {
                val sendAccessor = StompHeaderAccessor.create(StompCommand.SEND)
                sendAccessor.destination = "/app/wave/$roomId/chat"
                sendAccessor.user = WaveStompPrincipal(memberId)
                sendAccessor.setLeaveMutable(true)
                val message = MessageBuilder.createMessage(ByteArray(0), sendAccessor.messageHeaders)

                val ex = shouldThrow<LanglezException> {
                    interceptor.preSend(message, channel)
                }
                ex.status shouldBe 403
                ex.message shouldBe "wave.banned-user"
            }
        }

        When("정원 미만이고 밴되지 않은 사용자가 입장하면") {
            val room = WaveRoom(id = roomId, broadcasterId = 2L, title = "Open Room", maxParticipants = 4)
            every { waveRoomRepository.findById(roomId) } returns room
            every { viewerTracker.isBanned(roomId, memberId) } returns false
            every { viewerTracker.isViewer(roomId, memberId) } returns false
            every { viewerTracker.viewerCount(roomId) } returns 2L
            every { viewerTracker.join(any(), any(), any()) } returns Unit

            Then("정상적으로 입장이 허용된다") {
                val message = createSubscribeMessage(destination, memberId)
                val result = interceptor.preSend(message, channel)
                result shouldBe message
            }
        }
    }
})
