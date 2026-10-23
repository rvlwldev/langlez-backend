package com.langlez.redis.cache

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CacheProvider internal constructor(
    private val redisson: RedissonClient,
    private val registry: MeterRegistry,
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val codec = CacheCodec()
    private val caches = ConcurrentHashMap<String, Entry>()
    private val redisAvailable = AtomicBoolean(true)

    private val healthChecker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "cache-health").apply { isDaemon = true }
    }

    init {
        healthChecker.scheduleWithFixedDelay(::checkRedisHealth, HEALTH_CHECK_SECONDS, HEALTH_CHECK_SECONDS, TimeUnit.SECONDS)
    }

    fun getCache(name: String): Cache = caches.computeIfAbsent(name, ::create).cache

    internal fun checkRedisHealth() {
        try {
            redisson.keys.countExists(HEALTH_PROBE_KEY)
            if (redisAvailable.compareAndSet(false, true)) {
                logger.info("Redis 가 복구됐다. 로컬 폴백 캐시를 버린다")
                caches.values.forEach { it.local.clear() }
            }
        } catch (_: Exception) {
            markRedisDown()
        }
    }

    override fun close() {
        healthChecker.shutdownNow()
    }

    private fun create(name: String): Entry {
        val local = CaffeineCache(
            Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10_000).build(),
            codec,
        )
        val cache = Cache(name, RedisCache(name, redisson, codec), local, registry, redisAvailable::get, ::markRedisDown)
        return Entry(cache, local)
    }

    private fun markRedisDown() {
        if (redisAvailable.compareAndSet(true, false)) {
            logger.error("Redis 연결에 실패했다. 로컬 캐시로 대체한다")
        }
    }

    private class Entry(val cache: Cache, val local: CaffeineCache)

    companion object {
        private const val HEALTH_PROBE_KEY = "health:cache"
        private const val HEALTH_CHECK_SECONDS = 5L
    }
}
