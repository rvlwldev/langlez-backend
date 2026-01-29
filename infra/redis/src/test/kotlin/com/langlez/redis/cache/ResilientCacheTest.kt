package com.langlez.redis.cache

import com.langlez.common.jackson.JacksonConfiguration
import com.langlez.config.RedisConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.atomic.AtomicInteger

@Service
class TestCacheService {
    val callCount = AtomicInteger(0)

    @Cacheable(cacheNames = ["testCache"], key = "#key", sync = true)
    fun getCachedData(key: String): String {
        callCount.incrementAndGet()
        return "Data for $key"
    }
}

@SpringBootTest(
    classes = [ResilientCacheTest.TestApp::class],
    properties = ["spring.data.redis.timeout=2s"],
)
class ResilientCacheTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @SpringBootApplication(scanBasePackages = ["com.langlez.redis.cache"])
    @EnableCaching
    @Import(RedisConfiguration::class, JacksonConfiguration::class, ResilientCacheConfiguration::class)
    class TestApp

    @Autowired
    private lateinit var cacheService: TestCacheService

    @Autowired
    private lateinit var cacheManager: ResilientCacheManager

    companion object {
        val redisContainer =
            GenericContainer(DockerImageName.parse("redis:7.2"))
                .withExposedPorts(6379)

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
        test("`레질리언트 캐시` - Redis 장애 시 로컬 캐시로 자동 전환 및 복구 후 마이그레이션") {
            // 1. 초기 상태: Redis 사용 가능
            cacheManager.isRedisAvailable shouldBe true
            val res1 = cacheService.getCachedData("key1")
            res1 shouldBe "Data for key1"
            cacheService.callCount.get() shouldBe 1

            // 2. 장애 모의: Redis 상태를 수동으로 다운 처리
            cacheManager.markRedisDown()
            cacheManager.isRedisAvailable shouldBe false

            // 로컬 캐시(Caffeine)로 전환되어야 함
            val resLocal = cacheService.getCachedData("key-in-local")
            resLocal shouldBe "Data for key-in-local"
            cacheService.callCount.get() shouldBe 2

            // 3. 복구 및 마이그레이션 모의
            // checkRedisAndMigrate 실행 (연결 확인 후 복구 시도)
            cacheManager.checkRedisAndMigrate()
            cacheManager.isRedisAvailable shouldBe true

            // 마이그레이션 후 Redis에서 데이터 조회 확인
            // 실제 환경에서는 로컬 캐시 데이터가 Redis로 이동했으므로,
            // 같은 키로 조회 시 메서드 실행 없이(callCount 증가 없음) 데이터를 가져와야 함
            val resFromRedis = cacheService.getCachedData("key-in-local")
            resFromRedis shouldBe "Data for key-in-local"
            cacheService.callCount.get() shouldBe 2
        }
    }
}
