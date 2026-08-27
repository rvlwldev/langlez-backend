package com.langlez.redis.cache

import com.langlez.core.cache.Cache
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

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
        runGuarded({ redis.put(key, value) }, { afterCommit { local.put(key, value) } })

    override fun <T : Any> putMany(entries: Map<out Any, T>) =
        runGuarded({ redis.putMany(entries) }, { afterCommit { local.putMany(entries) } })

    // read-through 적재는 커밋 전 값이 아니라 이미 커밋된 값이라 폴백도 미룰 이유가 없다.
    override fun putIfAbsent(key: Any, value: Any) =
        runGuarded({ redis.putIfAbsent(key, value) }, { local.putIfAbsent(key, value) })

    override fun <T : Any> putManyIfAbsent(entries: Map<out Any, T>) =
        runGuarded({ redis.putManyIfAbsent(entries) }, { local.putManyIfAbsent(entries) })

    override fun evict(key: Any) =
        runGuarded({ redis.evict(key) }, {}).also { local.evict(key) }

    override fun evictMany(keys: Collection<Any>) =
        runGuarded({ redis.evictMany(keys) }, {}).also { local.evictMany(keys) }

    /**
     * 로컬 폴백 쓰기도 커밋 이후로 미룬다.
     *
     * `RedisCache` 는 트랜잭션 중이면 afterCommit 에 쓰기를 미루는데, 폴백 경로가 즉시 쓰면
     * 롤백된 트랜잭션이 만든 값이 로컬 캐시에 남는다. 레디스가 죽은 동안 그 노드가
     * DB 에 없는 값을 계속 서빙하게 된다.
     */
    private fun afterCommit(write: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return write()

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = write()
        })
    }

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
