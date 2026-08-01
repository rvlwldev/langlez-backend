package com.langlez.redis.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.cache.CacheProvider
import com.langlez.redis.cache.ResilientCacheProvider
import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RedissonClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.cache.RedisCacheWriter
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Duration
import kotlin.random.Random

@Configuration
class ResilientCacheConfiguration {

    @Bean
    fun cacheProvider(redisson: RedissonClient, meterRegistry: MeterRegistry): CacheProvider =
        ResilientCacheProvider(redisson, meterRegistry)

    /**
     * profile / relationship 의 `@Cacheable` 이 남아 있는 동안만 유지한다.
     * 전부 `CacheProvider` 로 넘어가면 `@EnableCaching` 과 함께 제거한다.
     */
    @Deprecated(message = "use `CacheProvider` instead")
    @Bean
    @Primary
    fun cacheManager(connectionFactory: RedisConnectionFactory, objectMapper: ObjectMapper): RedisCacheManager {
        val serializer = RedisSerializationContext.SerializationPair
            .fromSerializer(GenericJackson2JsonRedisSerializer(objectMapper))

        val config = RedisCacheConfiguration
            .defaultCacheConfig()
            .disableCachingNullValues()
            .serializeValuesWith(serializer)
            .entryTtl(Duration.ofMinutes(10))

        val defaultWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory)
        val jitteredWriter = object : RedisCacheWriter by defaultWriter {
            override fun put(name: String, key: ByteArray, value: ByteArray, ttl: Duration?) {
                val jitteredTtl = Duration.ofMinutes(10).plusSeconds(Random.nextLong(0, 60))
                defaultWriter.put(name, key, value, jitteredTtl)
            }
        }

        return RedisCacheManager
            .builder(jitteredWriter)
            .cacheDefaults(config)
            .transactionAware()
            .build()
    }
}