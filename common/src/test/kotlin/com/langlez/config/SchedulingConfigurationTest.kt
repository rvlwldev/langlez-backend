package com.langlez.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.scheduling.config.ScheduledTaskRegistrar

class SchedulingConfigurationTest : BehaviorSpec({

    val config = SchedulingConfiguration()

    Given("SchedulingConfiguration 에서 taskScheduler 를 생성하면") {
        val scheduler = config.taskScheduler()

        Then("스레드명 접두사가 app-scheduled- 로 설정된다") {
            scheduler.threadNamePrefix shouldBe "app-scheduled-"
        }

        Then("풀 크기가 10으로 설정된다") {
            scheduler.scheduledThreadPoolExecutor.corePoolSize shouldBe 10
        }

        Then("취소 시 제거 정책이 활성화된다") {
            scheduler.scheduledThreadPoolExecutor.removeOnCancelPolicy shouldBe true
        }

        When("ScheduledTaskRegistrar 에 등록하면") {
            val registrar = ScheduledTaskRegistrar()
            config.configureTasks(registrar)

            Then("동일한 TaskScheduler 가 registrar 에 주입된다") {
                registrar.scheduler shouldBe scheduler
            }
        }
    }
})
