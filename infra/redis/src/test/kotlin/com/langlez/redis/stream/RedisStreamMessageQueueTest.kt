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

    val queue = RedisStreamMessageQueue(redissonClient)

    afterSpec {
        redissonClient.shutdown()
        redis.stop()
    }

    Given("RedisStreamMessageQueue.publish 호출 시") {

        When("topic, key, payload를 전달하면") {
            val topic = "publish-test-topic"
            queue.publish(topic, "member-1", "hello-world")

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
            queue.publish(topic, "k1", "payload-1")
            queue.publish(topic, "k2", "payload-2")

            Then("두 엔트리가 순서대로 모두 추가된다") {
                val entries = redissonClient.getStream<String, String>(topic)
                    .range(StreamMessageId.MIN, StreamMessageId.MAX)

                entries.size shouldBe 2
                val payloads = entries.values.map { it["payload"] }
                payloads shouldBe listOf("payload-1", "payload-2")
            }
        }
    }
})
