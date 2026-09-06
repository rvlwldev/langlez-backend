package com.langlez.wave.application

import com.langlez.redis.distributedLock.DistributedLock
import com.langlez.wave.domain.WaveRepository
import com.langlez.wave.domain.WaveRoom
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant

/**
 * 버려진 방 리퍼.
 *
 * `@Scheduled` 가 빠지면 죽은 방이 목록에 영구히 남고, `@DistributedLock` 이 빠지면
 * 인스턴스 수만큼 같은 방을 동시에 닫는다. 둘 다 실행 시점에야 드러나므로 어노테이션 수준에서 고정한다.
 * (`MemberSuspendReleaseSchedulerTest` 와 같은 이유다.)
 */
class WaveRoomReaperTest : BehaviorSpec({

    val repo = mockk<WaveRepository>()
    val service = mockk<WaveService>(relaxed = true)

    val reaper = WaveRoomReaper(repo, service)

    fun room(id: Long) = WaveRoom(id = id, broadcasterId = 1L, title = "영어 수다방")

    Given("리퍼 진입점은") {

        val reapAbandoned = WaveRoomReaper::class.java.getDeclaredMethod("reapAbandoned")

        When("어노테이션을 보면") {
            Then("10분마다 도는 @Scheduled 가 붙어 있다") {
                reapAbandoned.getAnnotation(Scheduled::class.java)
                    .shouldNotBeNull().cron shouldBe "0 */10 * * * *"
            }

            Then("중복 실행을 막는 @DistributedLock 이 함께 붙어 있다") {
                reapAbandoned.getAnnotation(DistributedLock::class.java)
                    .shouldNotBeNull().prefix shouldBe "lock:wave-room-reap"
            }
        }
    }

    Given("진행 중인 방을 훑을 때") {
        val now = Instant.parse("2026-09-06T00:00:00Z")

        When("여러 방이 걸리면") {
            Then("방마다 아무도 없는지 확인해 닫는다") {
                every { repo.findOpenStartedBefore(now, any()) } returns listOf(room(1L), room(2L))

                reaper.reapAbandonedStartedBefore(now)

                verify { service.closeIfAbandoned(1L) }
                verify { service.closeIfAbandoned(2L) }
            }
        }

        When("한 방에서 예외가 나면") {
            Then("나머지 방은 그대로 정리한다") {
                every { repo.findOpenStartedBefore(now, any()) } returns listOf(room(1L), room(2L))
                every { service.closeIfAbandoned(1L) } throws IllegalStateException("레디스 순단")

                reaper.reapAbandonedStartedBefore(now)

                // 앞 방에서 멈추면 그 뒤 방들이 영영 안 닫힌다.
                verify { service.closeIfAbandoned(2L) }
            }
        }

        When("한 주기에 가져오는 양은") {
            Then("상한이 걸려 있다") {
                val limit = slot<Int>()
                every { repo.findOpenStartedBefore(now, capture(limit)) } returns emptyList()

                reaper.reapAbandonedStartedBefore(now)

                // 다 비울 때까지 도는 루프를 두면 실패를 삼키는 구조에서 같은 행에 무한히 걸린다.
                limit.captured shouldBeGreaterThan 0
            }
        }
    }
})
