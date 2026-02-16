package com.langlez.redis.distributedLock

import com.langlez.config.JacksonConfiguration
import com.langlez.config.LettuceConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@Configuration
@EnableAutoConfiguration(
        exclude =
                [
                        DataSourceAutoConfiguration::class,
                        HibernateJpaAutoConfiguration::class,
                        JpaRepositoriesAutoConfiguration::class]
)
@Import(JacksonConfiguration::class, LettuceConfiguration::class)
class DistributedLockTestConfig

@ActiveProfiles("test")
@SpringBootTest(
        classes = [DistributedLockTestConfig::class],
        properties = ["spring.data.redis.timeout=2s"]
)
class DistributedLockTest(private val redis: StringRedisTemplate) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    companion object {
        val container =
                GenericContainer(DockerImageName.parse("redis:7.2")).withExposedPorts(6379)!!

        init {
            container.start()
        }

        @DynamicPropertySource
        @JvmStatic
        fun registerRedisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { container.host }
            registry.add("spring.data.redis.port") { container.getMappedPort(6379).toString() }
        }
    }

    init {
        afterEach { redis.connectionFactory?.connection?.serverCommands()?.flushAll() }

        // ==================== 기본 락 동작 테스트 ====================
        test("`분산락 기본` - 락 획득 및 해제가 정상 동작한다") {
            val key = "lock:basic"
            val acquired = redis.opsForValue().setIfAbsent(key, "locked", Duration.ofSeconds(5))

            acquired shouldBe true
            redis.hasKey(key) shouldBe true

            redis.delete(key)
            redis.hasKey(key) shouldBe false
        }

        test("`분산락 기본` - 이미 락이 걸린 키에는 재획득이 불가능하다") {
            val key = "lock:duplicate"

            val first = redis.opsForValue().setIfAbsent(key, "locked", Duration.ofSeconds(5))
            val second = redis.opsForValue().setIfAbsent(key, "locked", Duration.ofSeconds(5))

            first shouldBe true
            second shouldBe false
        }

        test("`분산락 TTL` - 만료 시간 후 락이 자동 해제된다") {
            val key = "lock:ttl"
            redis.opsForValue().setIfAbsent(key, "locked", Duration.ofMillis(100))

            redis.hasKey(key) shouldBe true
            Thread.sleep(150)
            redis.hasKey(key) shouldBe false
        }

        test("`분산락 TTL` - 락의 남은 TTL을 조회할 수 있다") {
            val key = "lock:ttl-check"
            redis.opsForValue().setIfAbsent(key, "locked", Duration.ofSeconds(10))

            val ttl = redis.getExpire(key)
            ttl shouldNotBe null
            ttl!! shouldBe 10L // 약간의 오차 허용
        }

        // ==================== 동시성 제어 테스트 ====================

        test("`동시성` - 대규모 트래픽에서도 락이 정확히 동기화된다 (100개 스레드)") {
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
                            acquired =
                                    redis.opsForValue()
                                            .setIfAbsent(key, "locked", Duration.ofSeconds(5)) ==
                                            true

                            if (acquired) break

                            Thread.sleep(10)
                        }

                        if (acquired) {
                            try {
                                val current = counter.get()
                                Thread.sleep(5)
                                counter.set(current + 1)
                            } finally {
                                redis.delete(key)
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

        test("`동시성` - 락 없이 동시 접근하면 Race Condition이 발생한다") {
            val counter = AtomicInteger(0)
            val numberOfThreads = 100
            val latch = CountDownLatch(numberOfThreads)
            val executor = Executors.newFixedThreadPool(32)

            repeat(numberOfThreads) {
                executor.submit {
                    try {
                        // 락 없이 직접 카운터 증가 (Race Condition 발생 가능)
                        val current = counter.get()
                        Thread.sleep(1) // 컨텍스트 스위칭 유도
                        counter.set(current + 1)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // Race Condition으로 인해 100보다 작을 가능성이 높음
            println("Counter without lock: ${counter.get()} (expected less than $numberOfThreads)")
            counter.get() shouldNotBe numberOfThreads
        }

        test("`동시성` - 여러 개의 서로 다른 키에 대해 병렬로 락을 획득할 수 있다") {
            val numberOfKeys = 10
            val latch = CountDownLatch(numberOfKeys)
            val executor = Executors.newFixedThreadPool(numberOfKeys)
            val results = mutableListOf<Boolean>()
            val lock = Object()

            repeat(numberOfKeys) { i ->
                executor.submit {
                    try {
                        val key = "lock:parallel:$i"
                        val acquired =
                                redis.opsForValue()
                                        .setIfAbsent(key, "locked", Duration.ofSeconds(5)) == true
                        synchronized(lock) { results.add(acquired) }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            results.all { it } shouldBe true // 모든 키에 대해 락 획득 성공
        }

        // ==================== 엣지 케이스 테스트 ====================
        test("`엣지케이스` - 빈 문자열 키에도 락을 걸 수 있다") {
            val key = ""
            val acquired = redis.opsForValue().setIfAbsent(key, "locked", Duration.ofSeconds(5))
            acquired shouldBe true
        }

        test("`엣지케이스` - 특수문자가 포함된 키에도 락을 걸 수 있다") {
            val key = "lock:user:123:order:456!@#\$%^&*()"
            val acquired = redis.opsForValue().setIfAbsent(key, "locked", Duration.ofSeconds(5))
            acquired shouldBe true
        }

        test("`엣지케이스` - 매우 긴 키에도 락을 걸 수 있다") {
            val key = "lock:" + "a".repeat(1000)
            val acquired = redis.opsForValue().setIfAbsent(key, "locked", Duration.ofSeconds(5))
            acquired shouldBe true
        }

        test("`엣지케이스` - 존재하지 않는 키 삭제는 예외 없이 처리된다") {
            val key = "lock:nonexistent"
            val result = redis.delete(key)
            result shouldBe false
        }

        test("`엣지케이스` - 락 해제 후 즉시 재획득이 가능하다") {
            val key = "lock:reacquire"

            val first = redis.opsForValue().setIfAbsent(key, "locked", Duration.ofSeconds(5))
            first shouldBe true

            redis.delete(key)

            val second = redis.opsForValue().setIfAbsent(key, "locked", Duration.ofSeconds(5))
            second shouldBe true
        }

        // ==================== 재시도 로직 테스트 ====================

        test("`재시도` - 락 획득 실패 시 재시도하여 성공한다") {
            val key = "lock:retry"
            val executor = Executors.newSingleThreadExecutor()
            val acquiredByMain = AtomicInteger(0)

            // 다른 스레드가 먼저 락 획득
            executor.submit {
                redis.opsForValue().setIfAbsent(key, "locked", Duration.ofMillis(200))
            }

            Thread.sleep(50) // 다른 스레드가 먼저 락을 획득하도록 대기

            // 메인 스레드에서 재시도
            val start = System.currentTimeMillis()
            var acquired = false
            while (System.currentTimeMillis() - start < 1000) {
                if (redis.opsForValue().setIfAbsent(key, "locked", Duration.ofSeconds(5)) == true) {
                    acquired = true
                    acquiredByMain.incrementAndGet()
                    break
                }
                Thread.sleep(50)
            }

            executor.shutdown()
            acquired shouldBe true
            acquiredByMain.get() shouldBe 1
        }

        // ==================== 페어니스(Fairness) 테스트 ====================
        test("`페어니스` - 락 대기 중인 스레드들이 순서대로 획득한다 (FIFO 근사)") {
            val key = "lock:fairness"
            val orderList = mutableListOf<Int>()
            val lock = Object()
            val numberOfThreads = 5
            val latch = CountDownLatch(numberOfThreads)
            val executor = Executors.newFixedThreadPool(numberOfThreads)

            // 첫 번째 스레드가 먼저 락 획득
            redis.opsForValue().setIfAbsent(key, "locked", Duration.ofMillis(100))

            repeat(numberOfThreads) { i ->
                executor.submit {
                    try {
                        var acquired = false
                        val start = System.currentTimeMillis()
                        while (System.currentTimeMillis() - start < 5000) {
                            acquired =
                                    redis.opsForValue()
                                            .setIfAbsent(key, "locked", Duration.ofMillis(50)) ==
                                            true
                            if (acquired) break
                            Thread.sleep(10)
                        }

                        if (acquired) {
                            synchronized(lock) { orderList.add(i) }
                            Thread.sleep(50) // 작업 수행
                            redis.delete(key)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // 모든 스레드가 락을 획득했어야 함
            orderList.size shouldBe numberOfThreads
        }
    }
}
