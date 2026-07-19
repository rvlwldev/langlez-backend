package com.langlez.wave.domain

import com.langlez.core.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class WaveRoomTest : BehaviorSpec({

    Given("WaveRoom 생성 시") {
        When("최대 인원수가 4~8 범위 내(e.g., 4, 6, 8)이고 제목이 유효하면") {
            Then("정상적으로 생성된다") {
                val room4 = WaveRoom(broadcasterId = 1L, title = "Title 4", maxParticipants = 4)
                room4.maxParticipants shouldBe 4
                room4.title shouldBe "Title 4"

                val room8 = WaveRoom(broadcasterId = 1L, title = "Title 8", maxParticipants = 8)
                room8.maxParticipants shouldBe 8
            }
        }

        When("최대 인원수가 4 미만이면(e.g., 3)") {
            Then("400 예외가 발생한다") {
                val exception = shouldThrow<LanglezException> {
                    WaveRoom(broadcasterId = 1L, title = "Title", maxParticipants = 3)
                }
                exception.status shouldBe 400
                exception.message shouldBe "wave.invalid-max-participants"
            }
        }

        When("최대 인원수가 8 초과이면(e.g., 9)") {
            Then("400 예외가 발생한다") {
                val exception = shouldThrow<LanglezException> {
                    WaveRoom(broadcasterId = 1L, title = "Title", maxParticipants = 9)
                }
                exception.status shouldBe 400
                exception.message shouldBe "wave.invalid-max-participants"
            }
        }

        When("제목이 공백이면") {
            Then("400 예외가 발생한다") {
                val exception = shouldThrow<LanglezException> {
                    WaveRoom(broadcasterId = 1L, title = "   ", maxParticipants = 6)
                }
                exception.status shouldBe 400
                exception.message shouldBe "wave.invalid-title"
            }
        }
    }

    Given("updateTitle 호출 시") {
        val room = WaveRoom(broadcasterId = 1L, title = "Old Title", maxParticipants = 6)

        When("방송 진행 중이고 유효한 제목으로 변경하면") {
            Then("제목이 변경된다") {
                room.updateTitle("New Title")
                room.title shouldBe "New Title"
            }
        }

        When("새 제목이 공백이면") {
            Then("400 예외가 발생한다") {
                val exception = shouldThrow<LanglezException> {
                    room.updateTitle("")
                }
                exception.status shouldBe 400
                exception.message shouldBe "wave.invalid-title"
            }
        }

        When("방이 이미 종료된 상태에서 제목을 변경하려 하면") {
            val endedRoom = WaveRoom(broadcasterId = 1L, title = "Ended Room", maxParticipants = 6, endedAt = Instant.now())

            Then("409 예외가 발생한다") {
                val exception = shouldThrow<LanglezException> {
                    endedRoom.updateTitle("Changed Title")
                }
                exception.status shouldBe 409
                exception.message shouldBe "wave.already-ended"
            }
        }
    }
})
