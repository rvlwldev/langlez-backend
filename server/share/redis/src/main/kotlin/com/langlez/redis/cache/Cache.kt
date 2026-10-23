package com.langlez.redis.cache

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

class Cache internal constructor(
    private val name: String,
    private val redis: RedisCache,
    private val local: CaffeineCache,
    private val registry: MeterRegistry,
    private val isAvailable: () -> Boolean,
    private val onFailure: () -> Unit,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun <T : Any> get(key: Any, type: Class<T>): T? =
        runGuarded({ redis.get(key, type) }, { local.get(key, type) })
            .also { recordResult(hit = it != null) }

    fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T> =
        runGuarded({ redis.getMany(keys, type) }, { local.getMany(keys, type) })
            .also { found ->
                recordResult(hit = true, count = found.size)
                recordResult(hit = false, count = keys.size - found.size)
            }

    fun put(key: Any, value: Any) =
        runGuarded({ redis.put(key, value) }, { afterCommit { local.put(key, value) } })

    fun <T : Any> putMany(entries: Map<out Any, T>) =
        runGuarded({ redis.putMany(entries) }, { afterCommit { local.putMany(entries) } })

    fun putIfAbsent(key: Any, value: Any) =
        runGuarded({ redis.putIfAbsent(key, value) }, { local.putIfAbsent(key, value) })

    fun <T : Any> putManyIfAbsent(entries: Map<out Any, T>) =
        runGuarded({ redis.putManyIfAbsent(entries) }, { local.putManyIfAbsent(entries) })

    fun evict(key: Any) {
        runGuarded({ redis.evict(key) }, {})
        local.evict(key)
    }

    fun evictMany(keys: Collection<Any>) {
        runGuarded({ redis.evictMany(keys) }, {})
        local.evictMany(keys)
    }

    private fun afterCommit(write: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return write()

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = write()
        })
    }

    private fun <T> runGuarded(operation: () -> T, fallback: () -> T): T =
        if (!isAvailable()) {
            fallback()
        } else {
            runCatching { operation() }.getOrElse {
                logger.error("Redis 작업에 실패했다: {}", name, it)
                onFailure()
                fallback()
            }
        }

    private fun recordResult(hit: Boolean, count: Int = 1) {
        if (count == 0) return

        registry.counter("cache.result", "cache", name, "result", if (hit) "hit" else "miss")
            .increment(count.toDouble())
    }
}

inline fun <reified T : Any> Cache.get(key: Any): T? = get(key, T::class.javaObjectType)

inline fun <reified T : Any> Cache.getMany(keys: Collection<Any>): Map<Any, T> = getMany(keys, T::class.javaObjectType)
