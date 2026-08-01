package com.langlez.redis.cache

import com.langlez.core.cache.Cache
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory

class ResilientCache(
    private val name: String,
    private val redis: Cache,
    private val local: Cache,
    private val registry: MeterRegistry,
    private val isAvailable: () -> Boolean = { true },
    private val onFailure: () -> Unit = {},
) : Cache {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun <T : Any> get(key: Any, type: Class<T>): T? =
        runGuarded({ redis.get(key, type) }, { local.get(key, type) })
            .also { recordResult(hit = it != null) }

    override fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T> =
        runGuarded({ redis.getMany(keys, type) }, { local.getMany(keys, type) })
            .also { found ->
                recordResult(hit = true, count = found.size)
                recordResult(hit = false, count = keys.size - found.size)
            }

    override fun put(key: Any, value: Any) =
        runGuarded({ redis.put(key, value) }, { local.put(key, value) })

    override fun <T : Any> putMany(entries: Map<out Any, T>) =
        runGuarded({ redis.putMany(entries) }, { local.putMany(entries) })

    override fun evict(key: Any) =
        runGuarded({ redis.evict(key) }, {}).also { local.evict(key) }

    override fun evictMany(keys: Collection<Any>) =
        runGuarded({ redis.evictMany(keys) }, {}).also { local.evictMany(keys) }

    private fun <T> runGuarded(op: () -> T, fallback: () -> T): T =
        if (!isAvailable()) fallback()
        else runCatching { op() }.getOrElse { e ->
            logger.error("Redis operation failed for cache: $name", e)
            onFailure()
            fallback()
        }

    private fun recordResult(hit: Boolean, count: Int = 1) {
        if (count == 0) return

        registry.counter("cache.result", "cache", name, "result", if (hit) "hit" else "miss")
            .increment(count.toDouble())
    }
}
