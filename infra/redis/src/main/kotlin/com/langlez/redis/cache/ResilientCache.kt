package com.langlez.redis.cache

import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import java.util.concurrent.Callable

class ResilientCache(
    private val name: String,
    private val redisCache: Cache,
    private val localCache: Cache,
    private val onFailure: () -> Unit,
) : Cache {

    private val logger = LoggerFactory.getLogger(javaClass)

    private fun <T> runGuarded(op: () -> T, fallback: () -> T): T =
        try {
            op()
        } catch (e: Exception) {
            logger.warn("Redis operation failed for cache $name: ${e.message}")
            onFailure()
            fallback()
        }

    override fun getName(): String = name

    override fun getNativeCache(): Any = redisCache.nativeCache

    override fun get(key: Any): Cache.ValueWrapper? =
        runGuarded(op = { redisCache.get(key) }, fallback = { localCache.get(key) })

    override fun <T : Any?> get(key: Any, type: Class<T>?): T? =
        runGuarded(op = { redisCache.get(key, type) }, fallback = { localCache.get(key, type) })

    override fun <T : Any?> get(key: Any, valueLoader: Callable<T>): T? =
        runGuarded(op = { redisCache.get(key, valueLoader) }, fallback = { localCache.get(key, valueLoader) })

    override fun put(key: Any, value: Any?) =
        runGuarded(op = { redisCache.put(key, value) }, fallback = { localCache.put(key, value) })

    override fun evict(key: Any) =
        runGuarded(op = { redisCache.evict(key) }, fallback = { localCache.evict(key) })

    override fun clear() =
        runGuarded(op = { redisCache.clear() }, fallback = { localCache.clear() })

}
