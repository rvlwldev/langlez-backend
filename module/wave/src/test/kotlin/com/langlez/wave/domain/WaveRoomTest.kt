package com.langlez.wave.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

class WaveRoomTest : BehaviorSpec({

    Given("음성방을 만들 때") {

        When("제목이 비어 있으면") {
            Then("i18n 키를 담은 IllegalArgumentException 이 난다") {
                // 도메인은 HTTP 를 모른다. 상태코드 변환은 application 몫이다.
                shouldThrow<IllegalArgumentException> { WaveRoom(broadcasterId = 1L, title = "  ") }
                    .message shouldBe "wave.title.invalid"
            }
        }

        When("정원이 허용 범위를 벗어나면") {
            Then("i18n 키를 담은 IllegalArgumentException 이 난다") {
                shouldThrow<IllegalArgumentException> {
                    WaveRoom(broadcasterId = 1L, title = "방", maxParticipants = WaveRoom.MAX_PARTICIPANTS + 1)
                }.message shouldBe "wave.max-participants.invalid"
            }
        }
    }

    Given("진행 중인 방을") {
        val room = WaveRoom(broadcasterId = 1L, title = "방")

        When("종료하면") {
            val at = Instant.now()
            room.end(at)

            Then("종료 시각이 남는다") {
                room.isEnded() shouldBe true
                room.endedAt shouldBe at
            }

            Then("한 번 더 종료해도 처음 시각이 유지된다") {
                room.end(at.plusSeconds(60))
                room.endedAt shouldBe at
            }

            Then("제목을 바꿀 수 없다") {
                shouldThrow<IllegalArgumentException> { room.updateTitle("새 제목") }
                    .message shouldBe "wave.room.already-ended"
            }
        }
    }

    Given("제목을 바꿀 때") {
        When("빈 제목이면") {
            Then("거부한다") {
                val room = WaveRoom(broadcasterId = 1L, title = "방")
                shouldThrow<IllegalArgumentException> { room.updateTitle(" ") }
                    .message shouldBe "wave.title.invalid"
                room.title.shouldNotBeNull() shouldBe "방"
            }
        }
    }
})
