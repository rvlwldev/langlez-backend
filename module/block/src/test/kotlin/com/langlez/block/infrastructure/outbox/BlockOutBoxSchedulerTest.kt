package com.langlez.block.infrastructure.outbox

import com.langlez.redis.distributedLock.DistributedLock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.scheduling.annotation.Scheduled

/**
 * 스케줄러가 "조용히 안 도는" 조합을 어노테이션 수준에서 고정한다.
 *
 * `@Scheduled` 만 붙고 `@DistributedLock` 이 빠지면 인스턴스 수만큼 같은 행이 중복 발행되고,
 * 반대로 `@DistributedLock` 만 붙으면 아무도 발행하지 않는다. 둘 다 실행 시점에야 드러난다.
 */
class BlockOutBoxSchedulerTest : BehaviorSpec({

    Given("아웃박스 발행 스케줄러는") {

        val send = BlockOutBoxScheduler::class.java.getDeclaredMethod("send")

        When("send() 를 보면") {
            Then("2초마다 도는 @Scheduled 가 붙어 있다") {
                send.getAnnotation(Scheduled::class.java).shouldNotBeNull().cron shouldBe "*/2 * * * * *"
            }

            Then("중복 발행을 막는 @DistributedLock 이 함께 붙어 있다") {
                send.getAnnotation(DistributedLock::class.java)
                    .shouldNotBeNull().prefix shouldBe "lock:block-outbox"
            }
        }
    }

    Given("아웃박스 이력 이관 스케줄러는") {

        val archive = BlockOutBoxHistoryScheduler::class.java.getDeclaredMethod("archive")

        When("archive() 를 보면") {
            Then("매일 아침 6시에 도는 @Scheduled 가 붙어 있다") {
                archive.getAnnotation(Scheduled::class.java).shouldNotBeNull().cron shouldBe "0 0 6 * * *"
            }

            Then("중복 실행을 막는 @DistributedLock 이 함께 붙어 있다") {
                archive.getAnnotation(DistributedLock::class.java)
                    .shouldNotBeNull().prefix shouldBe "lock:block-outbox-history"
            }
        }
    }
})
