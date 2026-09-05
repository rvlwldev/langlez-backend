package com.langlez.kafka.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldBeTypeOf
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.task.VirtualThreadTaskExecutor
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.listener.ContainerProperties.AckMode
import org.springframework.kafka.listener.DefaultErrorHandler

/**
 * `spring.kafka.listener.*` 가 실제 컨테이너까지 도달하는지 확인한다.
 *
 * 이 테스트가 지키려는 회귀는 하나다 — 누가 `KafkaConfiguration` 에서
 * `configurer.configure(...)` 호출을 빼는 것. 빼도 컴파일과 다른 테스트는 전부 통과하고,
 * `ackMode` 만 조용히 `BATCH` 로 돌아간다. 아래 `RECORD` 단언이 그때 깨진다.
 *
 * 단언은 팩토리가 아니라 `createContainer` 로 만든 실제 컨테이너에서 읽는다.
 * `@KafkaListener` 가 받는 것과 같은 경로라, 팩토리에만 값이 얹히고 컨테이너로는
 * 안 넘어가는 경우까지 잡힌다.
 */
class KafkaListenerContainerFactoryTest : BehaviorSpec({

    val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration::class.java))
        .withUserConfiguration(KafkaConfiguration::class.java)
        .withPropertyValues(
            "spring.kafka.bootstrap-servers=localhost:9092",
            // application.yml 과 같은 값. 여기 값이 컨테이너까지 도달하는지가 검증 대상이다.
            "spring.kafka.listener.ack-mode=record",
            "spring.kafka.listener.concurrency=3",
        )

    Given("application.yml 과 같은 spring.kafka.listener.* 로 컨텍스트를 띄우면") {

        Then("ackMode 가 RECORD 다 — configure 호출이 빠지면 BATCH 로 돌아간다") {
            runner.run { ctx ->
                ctx.container().containerProperties.ackMode shouldBe AckMode.RECORD
            }
        }

        Then("concurrency 는 yml 값 3 이다") {
            runner.run { ctx ->
                ctx.container().concurrency shouldBe 3
            }
        }

        Then("리스너는 가상 스레드 executor 로 돈다 — configure 가 덮어쓰면 안 된다") {
            runner.run { ctx ->
                ctx.container().containerProperties.listenerTaskExecutor
                    .shouldBeTypeOf<VirtualThreadTaskExecutor>()
            }
        }

        Then("에러 핸들러는 우리가 선언한 DefaultErrorHandler 빈 그대로다") {
            runner.run { ctx ->
                val ours = ctx.getBean(DefaultErrorHandler::class.java)
                ctx.container().commonErrorHandler shouldBeSameInstanceAs ours
            }
        }
    }

    Given("가상 스레드 전역 설정이 켜져 있으면") {
        // 이때 configure() 가 자기 SimpleAsyncTaskExecutor 로 갈아끼운다.
        // 우리 executor 를 configure 뒤에 얹는 순서가 아니면 여기서 깨진다.
        val virtual = runner.withPropertyValues("spring.threads.virtual.enabled=true")

        Then("그래도 우리 executor 가 남는다") {
            virtual.run { ctx ->
                ctx.container().containerProperties.listenerTaskExecutor
                    .shouldBeTypeOf<VirtualThreadTaskExecutor>()
            }
        }
    }
})

private fun org.springframework.context.ApplicationContext.container() =
    getBean("kafkaListenerContainerFactory", ConcurrentKafkaListenerContainerFactory::class.java)
        .createContainer("regression-check")
