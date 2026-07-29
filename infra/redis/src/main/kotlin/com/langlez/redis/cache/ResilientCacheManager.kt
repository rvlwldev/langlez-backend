package com.langlez.redis.cache

import com.fasterxml.jackson.databind.ObjectMapper
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ResilientCacheManager(
    private val redisCacheManager: RedisCacheManager,
    private val caffeineCacheManager: CaffeineCacheManager,
    private val connectionFactory: RedisConnectionFactory,
    private val redissonClient: RedissonClient,
    objectMapper: ObjectMapper,
) : CacheManager {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val serializer = GenericJackson2JsonRedisSerializer(objectMapper)

    private val caches = ConcurrentHashMap<String, Cache>()
    private val isRedisAvailable = AtomicBoolean(true)

    override fun getCache(name: String): Cache? = caches.getOrPut(name) {
        val redisCache = redisCacheManager.getCache(name)
            ?: return@getOrPut caffeineCacheManager.getCache(name)!!
        val localCache = caffeineCacheManager.getCache(name)!!

        return@getOrPut ResilientCache(name, redisCache, localCache) { markRedisDown() }
    }

    override fun getCacheNames(): Collection<String> = caches.keys

    @Scheduled(fixedDelay = 5000)
    fun checkRedisAndMigrate() {
        try {
            connectionFactory.connection.use { it.ping() }
            if (isRedisAvailable.compareAndSet(false, true)) {
                logger.debug("Redis is back online! Starting migration from local to Redis...")
                executeMigrationWithLock()
            }
        } catch (_: Exception) {
            markRedisDown()
        }
    }

    private fun markRedisDown() {
        if (isRedisAvailable.compareAndSet(true, false)) {
            logger.error("Redis connection failed. Falling back to local cache.")
        }
    }

    private fun executeMigrationWithLock() {
        val lock = redissonClient.getLock("lock:resilient-cache:migration")
        val acquired = try {
            lock.tryLock(0, 60, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (acquired) {
            try {
                migrateLocalToRedis()
            } finally {
                if (lock.isHeldByCurrentThread) {
                    lock.unlock()
                }
            }
        } else {
            logger.info("Another WAS instance is performing local to Redis migration. Skipping.")
        }
    }

    private fun migrateLocalToRedis() {
        connectionFactory.connection.use { connection ->
            caches.keys.forEach { name ->
                val localCache = caffeineCacheManager.getCache(name) as? CaffeineCache ?: return@forEach
                val map = localCache.nativeCache.asMap()
                if (map.isNotEmpty()) {
                    val batchMap = mutableMapOf<ByteArray, ByteArray>()
                    map.forEach { (key, value) ->
                        if (value != null) {
                            val redisKey = "$name::$key".toByteArray(Charsets.UTF_8)
                            val redisValue = serializer.serialize(value)
                            if (redisValue != null) {
                                batchMap[redisKey] = redisValue
                            }
                        }
                    }
                    if (batchMap.isNotEmpty()) {
                        runCatching { connection.mSet(batchMap) }
                    }
                    localCache.clear()
                }
            }
        }
    }
}
