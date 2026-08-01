package com.langlez.redis.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.langlez.core.cache.Cache
import com.langlez.core.cache.CacheProvider
import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import com.github.benmanes.caffeine.cache.Cache as NativeCache

class ResilientCacheProvider(
    private val redisson: RedissonClient,
    private val registry: MeterRegistry,
) : CacheProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val caches = ConcurrentHashMap<String, CacheAggregate>()
    private val isRedisAvailable = AtomicBoolean(true)

    override fun getCache(name: String): Cache = caches.computeIfAbsent(name, ::createCacheAggregate).aggregate

    private fun createCacheAggregate(name: String): CacheAggregate {
        val redis = RedisCache(name, redisson)
        val local = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build<Any, Any>()
        val aggregate = ResilientCache(name, redis, CaffeineCache(local), registry, isRedisAvailable::get) { markRedisDown() }

        return CacheAggregate(aggregate, redis, local)
    }

    @Scheduled(cron = "*/5 * * * * *")
    fun checkRedisAndMigrate() {
        try {
            redisson.keys.countExists(HEALTH_PROBE_KEY)
            if (isRedisAvailable.compareAndSet(false, true)) {
                logger.debug("Redis is back online! Starting migration from local to Redis...")
                Thread.sleep(Random.nextLong(0, 3000))
                migrateLocalToRedis()
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

    /**
     * Redis 가 죽어 있는 동안 로컬에만 쌓인 값을 Redis 로 올리고 로컬을 비운다.
     *
     * 분산 락을 걸지 않는다. 로컬 Caffeine 은 프로세스 안에만 있어서 각 인스턴스는
     * 자기 로컬만 올릴 수 있다. 경합할 대상이 없다.
     * 락으로 한 대만 통과시키면 나머지 인스턴스의 로컬이 영영 이관되지 않고 남아,
     * 다음 장애 때 그 stale 값이 되살아난다.
     *
     * 적재는 `RedisCache.putMany`(RBatch)로 내려보낸다. 직접 mSet 을 부르면 TTL 이 안 걸려
     * 이관된 키만 영구 잔존하고, 키 인코딩도 어댑터와 어긋나 이후 조회가 전부 미스가 된다.
     *
     * 로컬 비우기는 적재에 성공한 키만. 실패한 채로 비우면 Redis 에도 로컬에도 값이 없다.
     */
    private fun migrateLocalToRedis() = caches.forEach { (name, holder) ->
        val entries = holder.local.asMap().toMap()
        if (entries.isEmpty()) return@forEach

        runCatching { holder.redis.putMany(entries) }
            .onSuccess { entries.keys.forEach(holder.local::invalidate) }
            .onFailure { e ->
                logger.error("Local to Redis migration failed for cache: $name", e)
                markRedisDown()
            }
    }

    private class CacheAggregate(
        val aggregate: Cache,
        val redis: RedisCache,
        val local: NativeCache<Any, Any>,
    )

    companion object {
        private const val HEALTH_PROBE_KEY = "health:resilient-cache"
    }
}
