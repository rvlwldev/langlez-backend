package com.langlez.consumer

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.KafkaContainer
import java.time.Duration
import java.util.UUID

@SpringBootTest(
    classes = [ConsumerApp::class],
    properties = [
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.listener.ack-mode=record",
        "spring.kafka.listener.concurrency=3",
    ],
)
class KafkaSupportTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var template: KafkaTemplate<String, String>

    @Autowired
    lateinit var listener: DemoListener

    @Autowired
    lateinit var factory: ConcurrentKafkaListenerContainerFactory<Any, Any>

    companion object {
        private val kafka = KafkaContainer("apache/kafka:4.3.1").also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
        }
    }

    private fun awaitUntil(timeoutMillis: Long = 30_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "시간 안에 조건이 만족되지 않았다" }
            Thread.sleep(200)
        }
    }

    init {
        Given("demo 토픽을 구독하는 리스너가 있을 때") {
            When("메시지를 보내면") {
                Then("리스너가 받는다") {
                    template.send("demo", "hello").get()

                    awaitUntil { "hello" in listener.received }
                    listener.received shouldContain "hello"
                }
            }
        }

        Given("spring.kafka.listener 프로퍼티를 설정하면") {
            When("리스너 컨테이너 팩토리를 보면") {
                Then("ackMode 가 반영돼 있다") {
                    factory.containerProperties.ackMode shouldBe ContainerProperties.AckMode.RECORD
                }
            }
        }

        Given("리스너가 재시도할 수 없는 예외를 던질 때") {
            When("메시지를 보내면") {
                Then("재시도 없이 demo.DLT 로 간다") {
                    template.send("demo", "bad").get()

                    val consumer = KafkaConsumer<String, String>(
                        mapOf(
                            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                            ConsumerConfig.GROUP_ID_CONFIG to "dlt-checker-${UUID.randomUUID()}",
                            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                        ),
                    )

                    consumer.use {
                        it.subscribe(listOf("demo.DLT"))
                        var values = emptyList<String>()
                        awaitUntil {
                            values = values + it.poll(Duration.ofMillis(500)).map { record -> record.value() }
                            "bad" in values
                        }
                        values shouldContain "bad"
                    }
                }
            }
        }
    }
}
