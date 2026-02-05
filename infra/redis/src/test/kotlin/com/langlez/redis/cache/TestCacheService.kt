package com.langlez.redis.cache

import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

@Service
class TestCacheService {
    val callCount = AtomicInteger(0)
    val evictCount = AtomicInteger(0)

    @Cacheable(cacheNames = ["testCache"], key = "#key", sync = true)
    fun getCachedData(key: String): String {
        callCount.incrementAndGet()
        return "Data for $key"
    }

    @Cacheable(cacheNames = ["testCache"], key = "#key")
    fun getSlowData(key: String): String {
        callCount.incrementAndGet()
        Thread.sleep(100) // 느린 연산 시뮬레이션
        return "Slow data for $key"
    }

    @CacheEvict(cacheNames = ["testCache"], key = "#key")
    fun evictCache(key: String) {
        evictCount.incrementAndGet()
    }

    @CacheEvict(cacheNames = ["testCache"], allEntries = true)
    fun clearCache() {
        evictCount.incrementAndGet()
    }

    fun resetCounters() {
        callCount.set(0)
        evictCount.set(0)
    }
}