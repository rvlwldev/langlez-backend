package com.langlez.redis.cache

import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import java.util.concurrent.Callable

class ResilientCache(
    private val name: String,
    private val redis: Cache,
    private val local: Cache,
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

    override fun getNativeCache(): Any = redis.nativeCache

    override fun get(key: Any): Cache.ValueWrapper? =
        runGuarded(op = { redis.get(key) }, fallback = { local.get(key) })

    override fun <T> get(key: Any, type: Class<T>?): T? =
        runGuarded(op = { redis.get(key, type) }, fallback = { local.get(key, type) })

    override fun <T> get(key: Any, valueLoader: Callable<T>): T? =
        runGuarded(op = { redis.get(key, valueLoader) }, fallback = { local.get(key, valueLoader) })

    override fun put(key: Any, value: Any?) =
        runGuarded(op = { redis.put(key, value) }, fallback = { local.put(key, value) })

    override fun evict(key: Any) =
        runGuarded(op = { redis.evict(key) }, fallback = { local.evict(key) })

    override fun clear() =
        runGuarded(op = { redis.clear() }, fallback = { local.clear() })

}
