package com.langlez.redis.distributedLock

import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.http.HttpStatus
import org.testcontainers.containers.GenericContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

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

        When("leaseTime가 0 이하일 때 (Redisson Watchdog 자동 갱신 설정)") {
            val key = "test:watchdog:lock:key"

            Then("작업 수행 중에도 락이 자동 연장되어 락을 유지하고 완료 후 해제된다") {
                val result = lockService.executeWithLock(key, 1000, 0, TimeUnit.MILLISECONDS) {
                    val rLock = redissonClient.getLock(key)
                    rLock.isLocked shouldBe true
                    rLock.isHeldByCurrentThread shouldBe true
                    "watchdog_success"
                }
                result shouldBe "watchdog_success"

                val rLock = redissonClient.getLock(key)
                rLock.isLocked shouldBe false
            }
        }

        When("다른 스레드가 이미 락을 점유하고 있고 throwOnFailure가 true일 때") {
            val key = "test:conflict:lock:key"

            Then("LanglezException(409 CONFLICT)이 발생한다") {
                val acquiredLatch = CountDownLatch(1)
                val releaseLatch = CountDownLatch(1)
                val holder = thread {
                    val lock = redissonClient.getLock(key)
                    lock.lock()
                    acquiredLatch.countDown()
                    releaseLatch.await()
                    lock.unlock()
                }
                acquiredLatch.await()

                try {
                    val exception = shouldThrow<LanglezException> {
                        lockService.executeWithLock(
                            key = key,
                            waitTime = 50,
                            leaseTime = 10,
                            unit = TimeUnit.MILLISECONDS,
                            throwOnFailure = true
                        ) {
                            "should_not_reach"
                        }
                    }
                    exception.status shouldBe HttpStatus.CONFLICT
                    exception.message shouldBe "common.conflict"
                } finally {
                    releaseLatch.countDown()
                    holder.join()
                }
            }
        }

        When("다른 스레드가 이미 락을 점유하고 있고 throwOnFailure가 false일 때") {
            val key = "test:no-throw:lock:key"

            Then("예외를 던지지 않고 null을 반환한다") {
                val acquiredLatch = CountDownLatch(1)
                val releaseLatch = CountDownLatch(1)
                val holder = thread {
                    val lock = redissonClient.getLock(key)
                    lock.lock()
                    acquiredLatch.countDown()
                    releaseLatch.await()
                    lock.unlock()
                }
                acquiredLatch.await()

                try {
                    val result = lockService.executeWithLock(
                        key = key,
                        waitTime = 50,
                        leaseTime = 10,
                        unit = TimeUnit.MILLISECONDS,
                        throwOnFailure = false
                    ) {
                        "should_not_reach"
                    }
                    result shouldBe null
                } finally {
                    releaseLatch.countDown()
                    holder.join()
                }
            }
        }
    }
})
