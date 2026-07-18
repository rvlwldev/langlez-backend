package com.langlez.redis.ratelimit

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer

class DailyRateLimiterTest : BehaviorSpec({

    val redis = GenericContainer("redis:7.0").withExposedPorts(6379)
    redis.start()

    val redissonClient: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
        }
    )

    val rateLimiter = DailyRateLimiter(redissonClient)

    afterSpec {
        redissonClient.shutdown()
        redis.stop()
    }

    Given("DailyRateLimiter tryConsume 호출 시") {

        When("제한 횟수 이내인 경우") {
            val key = "test:limit:under"
            val limit = 3

            Then("true를 반환하고 카운트가 누적된다") {
                rateLimiter.tryConsume(key, limit) shouldBe true
                rateLimiter.currentCount(key) shouldBe 1L

                rateLimiter.tryConsume(key, limit) shouldBe true
                rateLimiter.currentCount(key) shouldBe 2L

                rateLimiter.tryConsume(key, limit) shouldBe true
                rateLimiter.currentCount(key) shouldBe 3L
            }
        }

        When("제한 횟수를 초과한 경우") {
            val key = "test:limit:over"
            val limit = 2

            Then("limit 이내에서는 true, 초과 시 false를 반환한다") {
                rateLimiter.tryConsume(key, limit) shouldBe true
                rateLimiter.tryConsume(key, limit) shouldBe true
                rateLimiter.tryConsume(key, limit) shouldBe false
                rateLimiter.currentCount(key) shouldBe 3L
            }
        }
    }
})
