package com.langlez.redis.cache

import com.langlez.jackson.config.JacksonConfiguration
import com.langlez.redis.config.ResilientCacheConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@Configuration
@EnableCaching
@Import(JacksonConfiguration::class, ResilientCacheConfiguration::class, TestCacheService::class)
@EnableAutoConfiguration(exclude = [DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class, JpaRepositoriesAutoConfiguration::class])
class TestConfig

@SpringBootTest(classes = [TestConfig::class], properties = ["spring.data.redis.timeout=2s"])
@ActiveProfiles("test")
class ResilientCacheTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var cacheService: TestCacheService

    @Autowired
    private lateinit var cacheManager: ResilientCacheManager

    companion object {
        val container = GenericContainer(DockerImageName.parse("redis:7.2")).withExposedPorts(6379)!!

        init {
            container.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerRedisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { container.host }
            registry.add("spring.data.redis.port") { container.getMappedPort(6379).toString() }
        }
    }

    init {
        beforeEach {
            cacheService.resetCounters()
            cacheService.clearCache()
        }

        // ==================== 기본 캐시 동작 테스트 ====================
        test("`캐시 기본` - 첫 번째 호출은 실제 메서드를 실행한다") {
            val result = cacheService.getCachedData("key1")

            result shouldBe "Data for key1"

            cacheService.callCount.get() shouldBe 1
        }

        test("`캐시 기본` - 두 번째 호출은 캐시에서 반환한다") {
            cacheService.getCachedData("key1")
            cacheService.getCachedData("key1")

            cacheService.callCount.get() shouldBe 1 // 한 번만 호출
        }

        test("`캐시 기본` - 다른 키는 별도로 캐시된다") {
            cacheService.getCachedData("key1")
            cacheService.getCachedData("key2")
            cacheService.getCachedData("key1")
            cacheService.getCachedData("key2")

            cacheService.callCount.get() shouldBe 2 // key1, key2 각각 한 번씩
        }

        test("`캐시 기본` - Evict 후 다시 메서드가 실행된다") {
            val uniqueKey = "evict-test-${System.currentTimeMillis()}"
            cacheService.getCachedData(uniqueKey)
            cacheService.evictCache(uniqueKey)
            cacheService.getCachedData(uniqueKey)

            cacheService.callCount.get() shouldBe 2
        }

        test("`캐시 기본` - 전체 캐시 Clear 후 모든 키가 다시 로드된다") {
            cacheService.getCachedData("key1")
            cacheService.getCachedData("key2")
            cacheService.clearCache()
            cacheService.getCachedData("key1")
            cacheService.getCachedData("key2")

            cacheService.callCount.get() shouldBe 4 // 2번씩
        }

        // ==================== ResilientCache 동작 테스트 ====================
        test("`레질리언트` - 정상 상황에서 Redis 캐시가 사용된다") {
            cacheManager.isRedisAvailable shouldBe true

            cacheService.getCachedData("resilient-key")
            cacheManager.isRedisAvailable shouldBe true
        }

        test("`레질리언트` - Redis 장애 시 로컬 캐시로 자동 전환된다") {
            // Redis 장애 시뮬레이션
            cacheManager.markRedisDown()

            cacheManager.isRedisAvailable shouldBe false
            // 로컬 캐시가 반환되므로 서비스는 계속 동작
            val cache = cacheManager.getCache("testCache")
            cache shouldNotBe null
        }

        // ==================== 동시성 테스트 ====================
        test("`동시성` - 여러 스레드가 동시에 같은 키로 요청해도 한 번만 실행된다 (sync=true)") {
            val numberOfThreads = 10
            val latch = CountDownLatch(numberOfThreads)
            val executor = Executors.newFixedThreadPool(numberOfThreads)

            repeat(numberOfThreads) {
                executor.submit {
                    try {
                        cacheService.getCachedData("concurrent-key")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            cacheService.callCount.get() shouldBe 1 // sync=true이므로 한 번만 실행
        }

        test("`동시성` - 여러 스레드가 서로 다른 키로 요청하면 병렬 실행된다") {
            val numberOfThreads = 10
            val latch = CountDownLatch(numberOfThreads)
            val executor = Executors.newFixedThreadPool(numberOfThreads)

            repeat(numberOfThreads) { i ->
                executor.submit {
                    try {
                        cacheService.getCachedData("key-$i")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            cacheService.callCount.get() shouldBe numberOfThreads
        }

        // ==================== 엣지 케이스 테스트 ====================
        test("`엣지케이스` - null 키도 캐시된다") {
            // Kotlin에서 null key는 일반적으로 허용되지 않지만, 빈 문자열 테스트
            val result1 = cacheService.getCachedData("")
            val result2 = cacheService.getCachedData("")

            result1 shouldBe "Data for "
            cacheService.callCount.get() shouldBe 1
        }

        test("`엣지케이스` - 특수문자 키도 정상 캐시된다") {
            val specialKey = "user:123!@#\$%^&*()"
            val result1 = cacheService.getCachedData(specialKey)
            val result2 = cacheService.getCachedData(specialKey)

            result1 shouldBe "Data for $specialKey"
            cacheService.callCount.get() shouldBe 1
        }

        test("`엣지케이스` - 매우 긴 키도 정상 캐시된다") {
            val longKey = "a".repeat(500)
            val result1 = cacheService.getCachedData(longKey)
            val result2 = cacheService.getCachedData(longKey)

            cacheService.callCount.get() shouldBe 1
        }

        test("`엣지케이스` - 유니코드 키도 정상 캐시된다") {
            val unicodeKey = "한글키🔑emoji"
            val result1 = cacheService.getCachedData(unicodeKey)
            val result2 = cacheService.getCachedData(unicodeKey)

            result1 shouldBe "Data for $unicodeKey"
            cacheService.callCount.get() shouldBe 1
        }

        // ==================== 성능 테스트 ====================
        test("`성능` - 캐시 히트 시 응답 시간이 현저히 빠르다") {
            // 첫 번째 호출 (캐시 미스)
            val startMiss = System.currentTimeMillis()
            cacheService.getSlowData("perf-key")
            val durationMiss = System.currentTimeMillis() - startMiss

            // 두 번째 호출 (캐시 히트)
            val startHit = System.currentTimeMillis()
            cacheService.getSlowData("perf-key")
            val durationHit = System.currentTimeMillis() - startHit

            println("Cache miss: ${durationMiss}ms, Cache hit: ${durationHit}ms")

            durationMiss shouldNotBe 0
            (durationHit < durationMiss) shouldBe true
        }

        // ==================== 캐시 매니저 테스트 ====================
        test("`캐시매니저` - 캐시 이름들이 올바르게 등록된다") {
            cacheService.getCachedData("any-key")

            val cacheNames = cacheManager.cacheNames
            cacheNames.contains("testCache") shouldBe true
        }

        test("`캐시매니저` - 존재하지 않는 캐시 요청 시 새로 생성된다") {
            val cache = cacheManager.getCache("newCache")
            cache shouldNotBe null
        }
    }
}
