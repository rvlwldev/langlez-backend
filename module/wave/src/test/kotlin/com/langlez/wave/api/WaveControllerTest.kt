package com.langlez.wave.api

import com.langlez.wave.api.request.WaveChatSendRequest
import com.langlez.wave.api.request.WaveRoomCreateRequest
import com.langlez.wave.application.WaveService
import com.langlez.wave.domain.WaveChat
import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.domain.WaveSessionRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

class WaveControllerTest : BehaviorSpec({

    val service = mockk<WaveService>()
    val sessions = mockk<WaveSessionRepository>()
    val controller = WaveController(service, sessions)

    afterEach { clearMocks(service, sessions, answers = false) }

    fun room(
        id: Long = 1L,
        broadcasterId: Long = 10L,
        title: String = "테스트 방",
        maxParticipants: Int = 4,
        startedAt: Instant = Instant.now(),
    ) = WaveRoom(
        id = id,
        broadcasterId = broadcasterId,
        title = title,
        maxParticipants = maxParticipants,
        startedAt = startedAt,
    )

    Given("방 생성 시") {
        When("방 제목과 정원을 전달하면") {
            val created = room(id = 1L, broadcasterId = 10L, title = "한국어 프리토킹", maxParticipants = 6)
            every { service.createRoom(10L, "한국어 프리토킹", 6) } returns created

            val response = controller.createRoom(10L, WaveRoomCreateRequest("한국어 프리토킹", 6))

            Then("생성된 방 정보와 참여자 수 1(방장)을 반환한다") {
                response.id shouldBe 1L
                response.broadcasterId shouldBe 10L
                response.title shouldBe "한국어 프리토킹"
                response.maxParticipants shouldBe 6
                response.participantCount shouldBe 1
            }
        }
    }

    Given("방 목록 조회 시") {
        When("열려 있는 방이 있으면") {
            val room1 = room(id = 10L, title = "방 1")
            val room2 = room(id = 20L, title = "방 2")
            every { service.listOpenRooms(20, null) } returns listOf(room1, room2)
            every { sessions.participantCount(10L) } returns 3
            every { sessions.participantCount(20L) } returns 5

            val response = controller.listRooms(20, null)

            Then("SMEMBERS(participants) 대신 SCARD(participantCount)로 참여자 수를 조회한다") {
                response shouldHaveSize 2
                response[0].id shouldBe 10L
                response[0].participantCount shouldBe 3
                response[1].id shouldBe 20L
                response[1].participantCount shouldBe 5

                verify(exactly = 1) { sessions.participantCount(10L) }
                verify(exactly = 1) { sessions.participantCount(20L) }
                verify(exactly = 0) { sessions.participants(any()) }
            }
        }
    }

    Given("방 입장 요청 시") {
        When("회원이 입장을 요청하면") {
            every { service.join(10L, 1L) } returns Unit

            controller.join(1L, 10L)

            Then("WaveService.join 이 호출된다") {
                verify(exactly = 1) { service.join(10L, 1L) }
            }
        }
    }

    Given("방 퇴장 요청 시") {
        When("회원이 퇴장을 요청하면") {
            every { service.leave(10L, 1L) } returns Unit

            controller.leave(1L, 10L)

            Then("WaveService.leave 가 호출된다") {
                verify(exactly = 1) { service.leave(10L, 1L) }
            }
        }
    }

    Given("방 종료 요청 시") {
        When("방장이 종료를 요청하면") {
            every { service.end(10L, 1L) } returns Unit

            controller.end(1L, 10L)

            Then("WaveService.end 가 호출된다") {
                verify(exactly = 1) { service.end(10L, 1L) }
            }
        }
    }

    Given("채팅 전송 시") {
        When("메시지 내용을 보내면") {
            val chat = WaveChat(roomId = 10L, senderId = 1L, content = "안녕하세요")
            every { service.chat(10L, 1L, "안녕하세요") } returns chat

            val response = controller.sendChat(1L, 10L, WaveChatSendRequest("안녕하세요"))

            Then("전송된 채팅 응답을 반환한다") {
                response.roomId shouldBe 10L
                response.senderId shouldBe 1L
                response.content shouldBe "안녕하세요"
            }
        }
    }

    Given("최근 채팅 목록 조회 시") {
        When("방 id 로 조회하면") {
            val chat1 = WaveChat(roomId = 10L, senderId = 1L, content = "첫 번째")
            val chat2 = WaveChat(roomId = 10L, senderId = 2L, content = "두 번째")
            every { service.recentChats(10L, 1L) } returns listOf(chat1, chat2)

            val response = controller.listChats(1L, 10L)

            Then("최근 채팅 목록을 반환한다") {
                response shouldHaveSize 2
                response[0].content shouldBe "첫 번째"
                response[1].content shouldBe "두 번째"
            }
        }
    }
})
