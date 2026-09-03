package com.langlez.follow.infrastructure.outbox

import com.langlez.redis.distributedLock.DistributedLock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.scheduling.annotation.Scheduled

/**
 * `@Scheduled` 만 붙고 `@DistributedLock` 이 빠지면 인스턴스 수만큼 같은 행을 중복 삭제 시도하고,
 * 반대로 `@DistributedLock` 만 붙으면 아무도 정리하지 않아 `follow_event_outbox_history` 가 무한 증가한다.
 */
class FollowOutBoxHistoryCleanupSchedulerTest : BehaviorSpec({

    Given("follow 아웃박스 이력 정리 스케줄러는") {

        val clean = FollowOutBoxHistoryCleanupScheduler::class.java.getDeclaredMethod("clean")

        When("clean() 을 보면") {
            Then("매일 아침 6시 30분에 도는 @Scheduled 가 붙어 있다") {
                clean.getAnnotation(Scheduled::class.java).shouldNotBeNull().cron shouldBe "0 30 6 * * *"
            }

            Then("중복 실행을 막는 @DistributedLock 이 함께 붙어 있다") {
                clean.getAnnotation(DistributedLock::class.java)
                    .shouldNotBeNull().prefix shouldBe "lock:follow-outbox-history-cleanup"
            }
        }
    }
})
