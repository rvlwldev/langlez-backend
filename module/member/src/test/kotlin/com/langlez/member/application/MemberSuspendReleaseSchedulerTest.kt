package com.langlez.member.application

import com.langlez.redis.distributedLock.DistributedLock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.scheduling.annotation.Scheduled

/**
 * 스케줄러가 "조용히 안 도는" 조합을 어노테이션 수준에서 고정한다.
 *
 * `@Scheduled` 가 빠지면 만료 해제가 아예 안 돌아 기간 정지가 다시 영구 정지가 되고,
 * `@DistributedLock` 이 빠지면 인스턴스 수만큼 같은 회원을 동시에 풀며 `@Version` 경합을 낸다.
 * 둘 다 실행 시점에야 드러난다.
 *
 * `MemberSuspendReleaseIntegrationTest` 는 인자를 받는 `releaseExpiredBefore` 를 직접 부르므로
 * 진입점인 `releaseExpired()` 의 어노테이션이 빠져도 잡지 못한다. 그 구멍을 여기서 막는다.
 */
class MemberSuspendReleaseSchedulerTest : BehaviorSpec({

    Given("정지 만료 해제 스케줄러는") {

        val releaseExpired = MemberSuspendReleaseScheduler::class.java.getDeclaredMethod("releaseExpired")

        When("releaseExpired() 를 보면") {
            Then("10분마다 도는 @Scheduled 가 붙어 있다") {
                releaseExpired.getAnnotation(Scheduled::class.java)
                    .shouldNotBeNull().cron shouldBe "0 */10 * * * *"
            }

            Then("중복 실행을 막는 @DistributedLock 이 함께 붙어 있다") {
                releaseExpired.getAnnotation(DistributedLock::class.java)
                    .shouldNotBeNull().prefix shouldBe "lock:member-suspend-release"
            }
        }
    }
})
