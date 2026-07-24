package com.langlez.redis.distributedLock

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import java.util.concurrent.TimeUnit

class RedisLockServiceTest : BehaviorSpec({

    val redis = GenericContainer("redis:7.0").withExposedPorts(6379)
    redis.start()

    val redissonClient: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
        }
    )

    val lockService = RedisLockService(redissonClient)

    afterSpec {
        redissonClient.shutdown()
        redis.stop()
    }

    Given("RedisLockService 테스트") {
        When("락 획득 후 작업 실행 시") {
            val key = "test:lock:key"

            Then("정상적으로 작업을 수행하고 락을 해제한다") {
                val result = lockService.executeWithLock(key, 3, 10, TimeUnit.SECONDS) {
                    "success"
                }
                result shouldBe "success"
            }
        }
    }
})
