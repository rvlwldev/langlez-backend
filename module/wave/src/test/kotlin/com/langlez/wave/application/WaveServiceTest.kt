package com.langlez.wave.application

import com.langlez.core.MessageBroadcaster
import com.langlez.exception.LanglezException
import com.langlez.redis.distributedLock.DistributedLock
import com.langlez.wave.domain.WaveChat
import com.langlez.wave.domain.WaveRepository
import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.domain.WaveSessionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class WaveServiceTest : BehaviorSpec({

    val repo = mockk<WaveRepository>()
    val sessions = mockk<WaveSessionRepository>(relaxed = true)
    val broadcaster = mockk<MessageBroadcaster>(relaxed = true)

    val service = WaveService(repo, sessions, broadcaster)

    afterEach { clearMocks(repo, sessions, broadcaster, answers = false) }

    val host = 1L
    val roomId = 100L

    fun room(maxParticipants: Int = 4, ended: Boolean = false) =
        WaveRoom(id = roomId, broadcasterId = host, title = "영어 수다방", maxParticipants = maxParticipants)
            .apply { if (ended) end() }

    Given("방을 만들 때") {
        When("제목이 비어 있으면") {
            Then("400 으로 변환해 돌려준다") {
                // 도메인의 IllegalArgumentException 을 그대로 흘리면 500 이 된다.
                shouldThrow<LanglezException> { service.createRoom(host, " ", 4) }.status.value() shouldBe 400
            }
        }

        When("정상 입력이면") {
            Then("방을 저장하고 만든 사람이 바로 참여자가 된다") {
                every { repo.save(any()) } answers { firstArg() }

                val created = service.createRoom(host, "영어 수다방", 4)

                created.broadcasterId shouldBe host
                verify { sessions.join(created.id, host) }
            }
        }
    }

    Given("방에 입장할 때") {

        When("정원이 이미 찼으면") {
            Then("409 로 거부한다") {
                every { repo.find(roomId) } returns room(maxParticipants = 4)
                every { sessions.joinIfNotFull(roomId, 9L, 4) } returns false

                shouldThrow<LanglezException> { service.join(roomId, 9L) }.status.value() shouldBe 409
            }
        }

        When("자리가 남아 있으면") {
            Then("정원과 등록을 한 번에 맡긴다") {
                every { repo.find(roomId) } returns room(maxParticipants = 4)
                every { sessions.joinIfNotFull(roomId, 9L, 4) } returns true

                service.join(roomId, 9L)

                // 정원을 따로 세어 보고 등록하면 그 사이에 남이 마지막 자리를 가져간다.
                verify { sessions.joinIfNotFull(roomId, 9L, 4) }
                verify(exactly = 0) { sessions.participants(roomId) }
            }
        }

        When("이미 종료된 방이면") {
            Then("409 로 거부한다") {
                every { repo.find(roomId) } returns room(ended = true)

                shouldThrow<LanglezException> { service.join(roomId, 9L) }.status.value() shouldBe 409
            }
        }

        When("없는 방이면") {
            Then("404 로 거부한다") {
                every { repo.find(roomId) } returns null

                shouldThrow<LanglezException> { service.join(roomId, 9L) }.status.value() shouldBe 404
            }
        }
    }

    Given("채팅을 보낼 때") {

        When("참여자가 아니면") {
            Then("403 으로 거부하고 아무것도 남기지 않는다") {
                every { repo.find(roomId) } returns room()
                every { sessions.isParticipant(roomId, 9L) } returns false

                shouldThrow<LanglezException> { service.chat(roomId, 9L, "안녕") }.status.value() shouldBe 403
                verify(exactly = 0) { sessions.appendChat(any(), any()) }
                verify(exactly = 0) { broadcaster.broadcast(any(), any()) }
            }
        }

        When("참여자가 보내면") {
            Then("링버퍼에 쌓고 방 토픽으로 브로드캐스트한다") {
                every { repo.find(roomId) } returns room()
                every { sessions.isParticipant(roomId, host) } returns true

                val chat = service.chat(roomId, host, "안녕")

                chat.content shouldBe "안녕"
                verify { sessions.appendChat(roomId, chat) }
                // SimpMessagingTemplate 직접 호출은 다중 인스턴스에서 조용히 누락된다.
                verify { broadcaster.broadcast("/topic/wave/$roomId/chat", chat) }
            }
        }

        When("본문이 비어 있으면") {
            Then("400 으로 거부한다") {
                shouldThrow<LanglezException> { service.chat(roomId, host, "   ") }.status.value() shouldBe 400
            }
        }
    }

    Given("최근 대화를 볼 때") {
        When("참여자가 아니면") {
            Then("403 으로 거부한다") {
                every { sessions.isParticipant(roomId, 9L) } returns false

                shouldThrow<LanglezException> { service.recentChats(roomId, 9L) }.status.value() shouldBe 403
            }
        }

        When("늦게 들어온 참여자가 보면") {
            Then("링버퍼에 남은 최근 대화를 그대로 돌려준다") {
                every { sessions.isParticipant(roomId, 9L) } returns true
                every { sessions.recentChats(roomId) } returns listOf(WaveChat(roomId, host, "먼저 온 말"))

                service.recentChats(roomId, 9L) shouldHaveSize 1
            }
        }
    }

    Given("방을 종료할 때") {

        When("방장이 아니면") {
            Then("403 으로 거부하고 대화도 지우지 않는다") {
                every { repo.find(roomId) } returns room()

                shouldThrow<LanglezException> { service.end(roomId, 9L) }.status.value() shouldBe 403
                verify(exactly = 0) { sessions.clear(roomId) }
            }
        }

        When("방장이 종료하면") {
            Then("방이 닫히고 대화·참여자가 즉시 사라진다") {
                val target = room()
                every { repo.find(roomId) } returns target
                every { repo.save(any()) } answers { firstArg() }

                service.end(roomId, host)

                target.isEnded() shouldBe true
                // 사라지는 채팅이다. 방이 끝나면 대화도 함께 끝난다.
                verify { sessions.clear(roomId) }
            }
        }
    }

    Given("방에서 나갈 때") {

        When("아직 남은 사람이 있으면") {
            Then("방은 그대로 둔다") {
                every { sessions.participants(roomId) } returns setOf(host)

                service.leave(roomId, 9L)

                verify { sessions.leave(roomId, 9L) }
                verify(exactly = 0) { repo.save(any()) }
            }
        }

        When("마지막 사람이 나가면") {
            Then("빈 방이 목록에 남지 않도록 방을 닫는다") {
                val target = room()
                every { sessions.participants(roomId) } returns emptySet()
                every { repo.find(roomId) } returns target
                every { repo.save(any()) } answers { firstArg() }

                service.leave(roomId, host)

                target.isEnded() shouldBe true
                verify { sessions.clear(roomId) }
            }
        }
    }

    Given("입장 진입점은") {
        When("어노테이션을 보면") {
            Then("조용히 건너뛰는 @DistributedLock 이 붙어 있지 않다") {
                // @DistributedLock 기본값은 waitMs=0, retries=0, throwOnFailure=false 다.
                // 락을 못 잡으면 본문을 통째로 건너뛰고 정상 반환하는데, join 은 Unit 이라
                // 그 스킵이 204 로 나간다. 사용자는 들어갔다고 믿지만 참여자 집합에 없다.
                // 정원 원자성은 WaveSessionRepository.joinIfNotFull 이 맡는다.
                WaveService::class.java.getDeclaredMethod("join", Long::class.java, Long::class.java)
                    .getAnnotation(DistributedLock::class.java).shouldBeNull()
            }
        }
    }

})
