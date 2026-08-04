package com.langlez.redis.cache

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import java.time.Instant

data class TestUserCache(val id: Long = 0L, val name: String = "", val createdAt: Instant = Instant.now())

class ResilientCacheManagerTest : BehaviorSpec({

    val redisContainer = GenericContainer("redis:7.0").withExposedPorts(6379)
    redisContainer.start()

    val redissonClient: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${redisContainer.host}:${redisContainer.getMappedPort(6379)}")
        }
    )

    val provider = ResilientCacheProvider(redissonClient, SimpleMeterRegistry())

    afterSpec {
        redissonClient.shutdown()
        redisContainer.stop()
    }

    Given("ResilientCacheProvider가 주어지고") {
        val cache = provider.getCache("test-user-cache")

        When("데이터를 저장했을 때") {
            val user = TestUserCache(1L, "hero", Instant.ofEpochMilli(1700000000000L))
            cache.put("user-1", user)

            Then("Instant 필드까지 온전히 캐시에서 조회된다") {
                val retrieved = cache.get("user-1", TestUserCache::class.java)
                retrieved shouldNotBe null
                retrieved?.id shouldBe user.id
                retrieved?.name shouldBe user.name
                retrieved?.createdAt shouldBe user.createdAt
            }
        }

        When("evict를 호출하면") {
            val user = TestUserCache(2L, "hero2", Instant.ofEpochMilli(1700000000000L))
            cache.put("user-2", user)
            cache.evict("user-2")

            Then("조회되지 않는다") {
                cache.get("user-2", TestUserCache::class.java) shouldBe null
            }
        }

        When("여러 건을 한 번에 저장하면") {
            val entries = mapOf(
                "bulk-1" to TestUserCache(11L, "bulk1", Instant.ofEpochMilli(1700000000000L)),
                "bulk-2" to TestUserCache(12L, "bulk2", Instant.ofEpochMilli(1700000000000L)),
            )
            cache.putMany(entries)

            Then("getMany로 전부 조회되고 evictMany로 전부 삭제된다") {
                val found = cache.getMany(entries.keys, TestUserCache::class.java)
                found.size shouldBe 2
                found["bulk-1"]?.name shouldBe "bulk1"
                found["bulk-2"]?.name shouldBe "bulk2"

                cache.evictMany(entries.keys)
                cache.getMany(entries.keys, TestUserCache::class.java).size shouldBe 0
            }
        }
    }
})
