package com.langlez.redis.cache

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.testcontainers.containers.GenericContainer
import java.time.Instant

data class TestUserCache(val id: Long = 0L, val name: String = "", val createdAt: Instant = Instant.now())

class ResilientCacheManagerTest : BehaviorSpec({

    val redisContainer = GenericContainer("redis:7.0").withExposedPorts(6379)
    redisContainer.start()

    val ptv = BasicPolymorphicTypeValidator.builder()
        .allowIfBaseType(Any::class.java)
        .build()

    val mapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY)
    }

    val connectionFactory = LettuceConnectionFactory(
        RedisStandaloneConfiguration(redisContainer.host, redisContainer.getMappedPort(6379))
    ).apply { afterPropertiesSet() }

    val redissonClient: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${redisContainer.host}:${redisContainer.getMappedPort(6379)}")
        }
    )

    val caffeineCacheManager = CaffeineCacheManager()

    val serializer = RedisSerializationContext.SerializationPair
        .fromSerializer(GenericJackson2JsonRedisSerializer(mapper))
    val config = RedisCacheConfiguration.defaultCacheConfig().serializeValuesWith(serializer)
    val redisCacheManager = RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build()

    val manager = ResilientCacheProvider(
        redisCacheManager = redisCacheManager,
        caffeineCacheManager = caffeineCacheManager,
        connectionFactory = connectionFactory,
        redisson = redissonClient,
        objectMapper = mapper,
    )

    afterSpec {
        redissonClient.shutdown()
        connectionFactory.destroy()
        redisContainer.stop()
    }

    Given("ResilientCacheManager가 주어지고") {
        val cache = manager.getCache("test-user-cache")!!

        When("데이터를 저장했을 때") {
            val user = TestUserCache(1L, "hero", Instant.ofEpochMilli(1700000000000L))
            cache.put("user-1", user)

            Then("정상적으로 캐시에서 조회되고 evict 시 제거된다") {
                val retrieved = cache.get("user-1", TestUserCache::class.java)
                retrieved shouldNotBe null
                retrieved?.id shouldBe user.id
                retrieved?.name shouldBe user.name
                retrieved?.createdAt shouldBe user.createdAt

                cache.evict("user-1")
                cache.get("user-1", TestUserCache::class.java) shouldBe null
            }
        }

        When("evict를 호출하면") {
            val user = TestUserCache(2L, "hero2", Instant.ofEpochMilli(1700000000000L))
            cache.put("user-2", user)
            cache.evict("user-2")

            Then("로컬 및 Redis 캐시 모두에서 완전히 삭제된다") {
                cache.get("user-2", TestUserCache::class.java) shouldBe null
                caffeineCacheManager.getCache("test-user-cache")?.get("user-2") shouldBe null
            }
        }
    }
})
