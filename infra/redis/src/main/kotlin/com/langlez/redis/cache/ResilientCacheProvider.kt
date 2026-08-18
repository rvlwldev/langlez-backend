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

        return CacheAggregate(aggregate, local)
    }

    @Scheduled(cron = "*/5 * * * * *")
    fun checkRedisHealth() {
        try {
            redisson.keys.countExists(HEALTH_PROBE_KEY)
            if (isRedisAvailable.compareAndSet(false, true)) {
                logger.info("Redis is back online. Discarding local fallback caches.")
                discardLocalCaches()
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
     * 복구 시 로컬 폴백 캐시를 버린다. Redis 로 올리지 않는다.
     *
     * 로컬 Caffeine 은 프로세스마다 따로다. 장애 중에는
     * (1) 다른 노드가 지운 키가 이 노드 로컬엔 그대로 남아 있고
     * (2) `ResilientCache` 의 로컬 폴백 쓰기는 트랜잭션 동기화를 안 타서
     *     롤백된 트랜잭션이 쓴 값도 남는다.
     * 이걸 복구 때 올리면 프로세스 안에만 있던 낡은/유령 값이 전역으로 승격된다.
     *
     * 캐시는 없어도 되는 데이터다. 버리면 다음 조회가 DB 를 한 번 더 칠 뿐이고
     * 정합성은 확실하다. 복구 직후 미스가 몰리는 비용은 감수한다.
     */
    private fun discardLocalCaches() = caches.values.forEach { it.local.invalidateAll() }

    private class CacheAggregate(
        val aggregate: Cache,
        val local: NativeCache<Any, Any>,
    )

    companion object {
        private const val HEALTH_PROBE_KEY = "health:resilient-cache"
    }
}
