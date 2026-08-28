package com.langlez.chat.infrastructure.outbox

import com.langlez.redis.distributedLock.DistributedLock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.scheduling.annotation.Scheduled

/**
 * `@Scheduled` 만 붙고 `@DistributedLock` 이 빠지면 인스턴스 수만큼 같은 행이 중복 이관되고,
 * 반대로 `@DistributedLock` 만 붙으면 아무도 이관하지 않는다. 둘 다 실행 시점에야 드러난다.
 */
class ChatOutBoxHistorySchedulerTest : BehaviorSpec({

    Given("채팅 아웃박스 이력 이관 스케줄러는") {

        val archive = ChatOutBoxHistoryScheduler::class.java.getDeclaredMethod("archive")

        When("archive() 를 보면") {
            Then("매일 아침 6시에 도는 @Scheduled 가 붙어 있다") {
                archive.getAnnotation(Scheduled::class.java).shouldNotBeNull().cron shouldBe "0 0 6 * * *"
            }

            Then("중복 실행을 막는 @DistributedLock 이 함께 붙어 있다") {
                archive.getAnnotation(DistributedLock::class.java)
                    .shouldNotBeNull().prefix shouldBe "lock:chat-outbox-history"
            }
        }
    }
})
