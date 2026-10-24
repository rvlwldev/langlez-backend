package com.langlez.consumer

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import java.util.UUID

class LokiLoggingTest : BehaviorSpec() {

    private val log = KotlinLogging.logger {}

    private fun start(vararg properties: String): ConfigurableApplicationContext =
        SpringApplicationBuilder(ConsumerApp::class.java)
            .web(WebApplicationType.NONE)
            .properties(*properties)
            .run()

    private fun awaitLines(logql: String, expected: String): String {
        val deadline = System.currentTimeMillis() + 20_000
        var body = TestLoki.query(logql)
        while (expected !in body && System.currentTimeMillis() < deadline) {
            Thread.sleep(300)
            body = TestLoki.query(logql)
        }
        return body
    }

    init {
        Given("push-url 을 설정한 앱이") {
            val service = "logger-${UUID.randomUUID()}"
            val app = start(
                "spring.application.name=$service",
                "langlez.logger.loki.push-url=${TestLoki.pushUrl}",
                "langlez.logger.loki.batch-timeout=200ms",
            )

            When("MDC 에 traceId 가 있는 채로 warn 로그를 남기면") {
                MDC.putCloseable("traceId", "trace-42").use { log.warn { "결제 지연 order=42" } }

                Then("Loki 에서 service·level 라벨로 찾을 수 있고 본문 JSON 에 traceId 가 들어 있다") {
                    val body = awaitLines("""{service="$service", level="WARN"}""", "결제 지연 order=42")

                    body shouldContain "결제 지연 order=42"
                    body shouldContain """\"traceId\":\"trace-42\""""
                }
            }

            When("info 로그를 남기면") {
                log.info { "가입 완료 member=7" }

                Then("level 라벨이 INFO 인 스트림으로 들어가고 WARN 스트림에는 섞이지 않는다") {
                    awaitLines("""{service="$service", level="INFO"}""", "가입 완료 member=7") shouldContain "가입 완료 member=7"
                    TestLoki.query("""{service="$service", level="WARN"}""") shouldNotContain "가입 완료 member=7"
                }
            }

            app.close()
        }

        Given("배치 전송 주기가 오기 전에 앱이 종료되면") {
            val service = "logger-${UUID.randomUUID()}"
            val app = start(
                "spring.application.name=$service",
                "langlez.logger.loki.push-url=${TestLoki.pushUrl}",
                "langlez.logger.loki.batch-timeout=1h",
            )

            When("종료 직전에 남긴 로그는") {
                log.error { "종료 직전 로그" }
                app.close()

                Then("종료하면서 보내므로 잃지 않는다") {
                    awaitLines("""{service="$service"}""", "종료 직전 로그") shouldContain "종료 직전 로그"
                }
            }
        }

        Given("push-url 이 없는 앱은") {
            val app = start("spring.application.name=logger-off")

            Then("Loki appender 를 붙이지 않는다") {
                val root = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(Logger.ROOT_LOGGER_NAME)
                root.getAppender("LOKI").shouldBeNull()
            }

            app.close()
        }
    }
}
