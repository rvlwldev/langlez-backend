package com.langlez

import com.langlez.config.SchedulingConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * WebSocket 하트비트 스레드풀과 애플리케이션 @Scheduled 스레드풀이
 * 독립적으로 격리되는지 검증하는 테스트 (README §5.2-7).
 */
class SchedulingThreadIsolationTest : BehaviorSpec({

    val runner = ApplicationContextRunner()
        .withUserConfiguration(
            SchedulingConfiguration::class.java,
            MockWebSocketBrokerConfiguration::class.java,
            TestScheduledConfiguration::class.java,
        )

    Given("WebSocket 하트비트용 messageBrokerTaskScheduler 빈이 존재하는 환경에서") {
        When("@Scheduled 작업이 실행되면") {
            Then("하트비트용 MessageBroker- 스레드가 아닌 app-scheduled- 전용 풀에서 실행된다") {
                runner.run { context ->
                    val primaryScheduler = context.getBean(TaskScheduler::class.java)
                    val appScheduler = context.getBean("taskScheduler", ThreadPoolTaskScheduler::class.java)
                    primaryScheduler shouldBe appScheduler

                    val task = context.getBean(TestScheduledTask::class.java)
                    val executed = task.latch.await(5, TimeUnit.SECONDS)
                    executed shouldBe true
                    task.executionThread.get() shouldStartWith "app-scheduled-"
                }
            }
        }
    }
}) {
    @Configuration
    @EnableScheduling
    open class TestScheduledConfiguration {
        @Bean
        open fun testScheduledTask(): TestScheduledTask = TestScheduledTask()
    }

    open class TestScheduledTask {
        val latch = CountDownLatch(1)
        val executionThread = AtomicReference<String>()

        @Scheduled(fixedDelay = 50)
        open fun run() {
            executionThread.set(Thread.currentThread().name)
            latch.countDown()
        }
    }

    @Configuration
    open class MockWebSocketBrokerConfiguration {
        @Bean(name = ["messageBrokerTaskScheduler"])
        open fun messageBrokerTaskScheduler(): TaskScheduler {
            val scheduler = ThreadPoolTaskScheduler()
            scheduler.setThreadNamePrefix("MessageBroker-")
            scheduler.initialize()
            return scheduler
        }
    }
}
