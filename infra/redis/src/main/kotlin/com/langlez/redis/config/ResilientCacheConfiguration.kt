package com.langlez.redis.config

import com.github.benmanes.caffeine.cache.Caffeine
import com.langlez.redis.cache.ResilientCacheManager
import org.slf4j.LoggerFactory
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Configuration
@EnableScheduling
class ResilientCacheConfiguration {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun caffeineCacheManager(): CaffeineCacheManager {
        val caffeineBuilder = Caffeine
            .newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(10000)

        return CaffeineCacheManager().apply { setCaffeine(caffeineBuilder) }
    }

    @Bean
    fun redisCacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        val serializer = RedisSerializationContext.SerializationPair
            .fromSerializer(GenericJackson2JsonRedisSerializer())
        val ttl = Duration.ofMinutes(10).plusSeconds(Random.nextLong(60))

        val config = RedisCacheConfiguration
            .defaultCacheConfig()
            .disableCachingNullValues()
            .serializeValuesWith(serializer)
            .entryTtl(ttl)

        return RedisCacheManager
            .builder(connectionFactory)
            .cacheDefaults(config)
            .transactionAware()
            .build()
    }

    @Bean
    @Primary
    fun cacheManager(
        redisCacheManager: RedisCacheManager,
        caffeineCacheManager: CaffeineCacheManager,
        connectionFactory: RedisConnectionFactory,
    ): ResilientCacheManager = ResilientCacheManager(redisCacheManager, caffeineCacheManager, connectionFactory)

}