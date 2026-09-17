package com.langlez.auth.infrastructure

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * `rotate` 는 비교+교체+만료를 Lua 스크립트 한 방으로 묶는다. mockk 로는 스크립트가 실제로
 * 원자적인지, 코덱이 Lua 와 `RBucket` 사이에서 일관되는지 확인할 수 없어 진짜 Redis 에 붙인다.
 */
class RedisSessionStoreTest : BehaviorSpec({

    val container = GenericContainer("redis:7.0").withExposedPorts(6379)
    container.start()

    val redisson: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${container.host}:${container.getMappedPort(6379)}")
        }
    )

    afterSpec {
        redisson.shutdown()
        container.stop()
    }

    val store = RedisSessionStore(redisson, refreshTokenTtlSecs = 1000)

    fun tokenBucket(id: Long) = redisson.getBucket<String>("refresh_token:$id", StringCodec.INSTANCE)
    fun deviceBucket(id: Long) = redisson.getBucket<String>("refresh_device:$id", StringCodec.INSTANCE)

    Given("rotate 를 호출할 때") {
        When("저장값이 from 과 같으면") {
            val id = 1L
            tokenBucket(id).set("old-token", Duration.ofSeconds(1000))

            Then("true 를 반환하고 값이 바뀐다") {
                val result = store.rotate(id, from = "old-token", to = "new-token")

                result shouldBe true
                tokenBucket(id).get() shouldBe "new-token"
            }
        }

        When("저장값이 from 과 다르면") {
            val id = 2L
            tokenBucket(id).set("actual-token", Duration.ofSeconds(1000))

            Then("false 를 반환하고 값은 그대로다") {
                val result = store.rotate(id, from = "wrong-token", to = "new-token")

                result shouldBe false
                tokenBucket(id).get() shouldBe "actual-token"
            }
        }

        When("교체에 성공하면") {
            val id = 3L
            tokenBucket(id).set("old-token", Duration.ofSeconds(1000))

            store.rotate(id, from = "old-token", to = "new-token")

            Then("TTL 이 다시 걸린다") {
                // 비교·교체·만료가 한 스크립트가 아니면 그 사이에 죽었을 때 TTL 없는
                // 영구 키(remainTimeToLive() == -1)가 남는다.
                tokenBucket(id).remainTimeToLive() shouldBeGreaterThan 0L
            }
        }

        When("같은 from 으로 동시에 두 번 호출되면") {
            val id = 4L
            tokenBucket(id).set("shared-token", Duration.ofSeconds(1000))

            Then("실제 스레드 둘 중 하나만 true 다") {
                val pool = Executors.newFixedThreadPool(2)
                val ready = CountDownLatch(2)
                val go = CountDownLatch(1)
                val successCount = AtomicInteger(0)

                val tasks = listOf("winner-A", "winner-B").map { candidate ->
                    pool.submit {
                        ready.countDown()
                        go.await()
                        if (store.rotate(id, from = "shared-token", to = candidate)) successCount.incrementAndGet()
                    }
                }

                ready.await()
                go.countDown()
                tasks.forEach { it.get(5, TimeUnit.SECONDS) }
                pool.shutdown()

                successCount.get() shouldBe 1
            }
        }
    }

    Given("bindDevice 를 호출할 때") {
        When("deviceId 가 null 이면") {
            val id = 5L
            deviceBucket(id).set("device-A", Duration.ofSeconds(1000))

            store.bindDevice(id, null)

            Then("바인딩을 지운다") {
                deviceBucket(id).get() shouldBe null
            }
        }

        When("deviceId 가 있으면") {
            val id = 6L

            store.bindDevice(id, "device-B")

            Then("그 기기로 바인딩한다") {
                deviceBucket(id).get() shouldBe "device-B"
            }
        }
    }
})
