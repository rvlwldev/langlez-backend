package com.langlez.redis.cache

import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.ConcurrentHashMap

class ResilientCacheManager(
    private val redisCacheManager: RedisCacheManager,
    private val caffeineCacheManager: CaffeineCacheManager,
    private val connectionFactory: RedisConnectionFactory,
) : CacheManager {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    var isRedisAvailable = true
        private set

    private val cacheNames = ConcurrentHashMap.newKeySet<String>()

    override fun getCache(name: String): Cache? {
        cacheNames.add(name)
        val redisCache = redisCacheManager.getCache(name)!!
        val localCache = caffeineCacheManager.getCache(name)!!

        return if (isRedisAvailable) {
            ResilientCache(name, redisCache, localCache) { markRedisDown() }
        } else {
            localCache
        }
    }

    fun markRedisDown() {
        if (isRedisAvailable) {
            log.error("Redis connection failed. Falling back to local cache.")
            isRedisAvailable = false
        }
    }

    override fun getCacheNames(): Collection<String> = cacheNames

    @Scheduled(fixedDelay = 5000)
    fun checkRedisAndMigrate() {
        try {
            val connection = connectionFactory.connection
            connection.use { it.ping() }

            if (!isRedisAvailable) {
                log.info("Redis is back online! Starting migration from local to Redis...")
                migrateLocalToRedis()
                isRedisAvailable = true
            }
        } catch (e: Exception) {
            markRedisDown()
        }
    }

    private fun migrateLocalToRedis() {
        cacheNames.forEach { name ->
            val localCache = caffeineCacheManager.getCache(name) as? CaffeineCache
            val redisCache = redisCacheManager.getCache(name)

            localCache?.nativeCache?.asMap()?.forEach { (key, value) ->
                try {
                    redisCache?.put(key, value)
                } catch (e: Exception) {
                    log.error("Failed to migrate key $key to Redis", e)
                }
            }
            localCache?.clear()
            log.info("Migrated and cleared local cache for name: $name")
        }
    }
}
