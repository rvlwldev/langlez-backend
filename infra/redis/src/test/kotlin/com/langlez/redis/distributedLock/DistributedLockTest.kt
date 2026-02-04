package com.langlez.redis.distributedLock

import com.langlez.common.jackson.JacksonConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Configuration
@EnableAutoConfiguration
@Import(JacksonConfiguration::class)
class DistributedLockTestConfig

@SpringBootTest(
    classes = [DistributedLockTestConfig::class],
    properties = ["spring.data.redis.timeout=2s"],
)
@ActiveProfiles("test")
@org.junit.jupiter.api.Disabled("Temporary disabled due to configuration context issues")
class DistributedLockTest(
    private val redisTemplate: RedisTemplate<String, Any>,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    companion object {
        val redisContainer = GenericContainer(DockerImageName.parse("redis:7.2")).withExposedPorts(6379)

        init {
            redisContainer.start()
        }

        @DynamicPropertySource
        @JvmStatic
        fun registerRedisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379).toString() }
        }
    }

    init {
        test("대규모 트래픽 시뮬레이션: 동시성 제어 테스트") {
            val key = "lock:counter"
            val counter = AtomicInteger(0)
            val numberOfThreads = 100
            val latch = CountDownLatch(numberOfThreads)
            val executor = Executors.newFixedThreadPool(32)

            repeat(numberOfThreads) {
                executor.submit {
                    try {
                        var acquired = false
                        val start = System.currentTimeMillis()
                        while (System.currentTimeMillis() - start < 10000) {
                            acquired = redisTemplate.opsForValue().setIfAbsent(key, "locked", Duration.ofSeconds(5)) == true
                            if (acquired) break
                            Thread.sleep(10)
                        }

                        if (acquired) {
                            try {
                                val current = counter.get()
                                Thread.sleep(5)
                                counter.set(current + 1)
                            } finally {
                                redisTemplate.delete(key)
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            counter.get() shouldBe numberOfThreads
        }
    }
}
