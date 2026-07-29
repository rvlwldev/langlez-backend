package com.langlez.redis.stream

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.api.StreamMessageId
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer

class RedisStreamMessageQueueTest : BehaviorSpec({

    val redis = GenericContainer("redis:7.0").withExposedPorts(6379)
    redis.start()

    val redissonClient: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
        }
    )

    val queue = RedisStreamMessageProducer(redissonClient)

    afterSpec {
        redissonClient.shutdown()
        redis.stop()
    }

    Given("RedisStreamMessageQueue.publish 호출 시") {

        When("topic, payload, key를 전달하면") {
            val topic = "publish-test-topic"
            queue.produce(topic, "hello-world", "member-1")

            Then("실제 Redis Stream에 key/payload 필드를 가진 엔트리가 추가된다") {
                val entries = redissonClient.getStream<String, String>(topic)
                    .range(StreamMessageId.MIN, StreamMessageId.MAX)

                entries.size shouldBe 1
                val fields = entries.values.first()
                fields["key"] shouldBe "member-1"
                fields["payload"] shouldBe "hello-world"
            }
        }

        When("동일한 topic에 여러 번 publish하면") {
            val topic = "publish-test-topic-multi"
            queue.produce(topic, "payload-1", "k1")
            queue.produce(topic, "payload-2", "k2")

            Then("두 엔트리가 순서대로 모두 추가된다") {
                val entries = redissonClient.getStream<String, String>(topic)
                    .range(StreamMessageId.MIN, StreamMessageId.MAX)

                entries.size shouldBe 2
                val payloads = entries.values.map { it["payload"] }
                payloads shouldBe listOf("payload-1", "payload-2")
            }
        }

        When("MINID 기반 자르기가 구동될 때 (방안 A)") {
            val topic = "publish-test-topic-minid"
            queue.produce(topic, "payload-1", "k1")
            queue.produce(topic, "payload-2", "k2")

            Then("24시간 이내의 최신 메시지는 트림 대상에 걸리지 않고 안전하게 보존된다") {
                val stream = redissonClient.getStream<String, String>(topic)
                val entries = stream.range(StreamMessageId.MIN, StreamMessageId.MAX)
                entries.size shouldBe 2
                entries.values.map { it["payload"] } shouldBe listOf("payload-1", "payload-2")
            }
        }
    }
})
