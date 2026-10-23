package com.langlez.redis.cache

import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import kotlin.random.Random

internal class RedisCache(
    private val name: String,
    private val redisson: RedissonClient,
    private val codec: CacheCodec,
    private val ttl: Duration = Duration.ofMinutes(10),
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun <T : Any> get(key: Any, type: Class<T>): T? =
        redisson.getBucket<String>(encode(key)).get()?.let { codec.decode(it, type) }

    fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T> {
        if (keys.isEmpty()) return emptyMap()

        val found = redisson.buckets.get<String>(*keys.map(::encode).toTypedArray())

        return keys.mapNotNull { key ->
            found[encode(key)]?.let { json -> codec.decode(json, type)?.let { key to it } }
        }.toMap()
    }

    fun put(key: Any, value: Any) = write(listOf(key)) {
        redisson.getBucket<String>(encode(key)).set(codec.encode(value), expiration())
    }

    fun <T : Any> putMany(entries: Map<out Any, T>) {
        if (entries.isEmpty()) return

        write(entries.keys) {
            val batch = redisson.createBatch()
            entries.forEach { (key, value) ->
                batch.getBucket<String>(encode(key)).setAsync(codec.encode(value), expiration())
            }
            batch.execute()
        }
    }

    fun putIfAbsent(key: Any, value: Any) {
        redisson.getBucket<String>(encode(key)).setIfAbsent(codec.encode(value), expiration())
    }

    fun <T : Any> putManyIfAbsent(entries: Map<out Any, T>) {
        if (entries.isEmpty()) return

        val batch = redisson.createBatch()
        entries.forEach { (key, value) ->
            batch.getBucket<String>(encode(key)).setIfAbsentAsync(codec.encode(value), expiration())
        }
        batch.execute()
    }

    fun evict(key: Any) = evictMany(listOf(key))

    fun evictMany(keys: Collection<Any>) {
        if (keys.isEmpty()) return
        write(keys) { delete(keys) }
    }

    private fun write(keys: Collection<Any>, commit: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return commit()

        delete(keys)

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                runCatching(commit).onFailure { logger.warn("커밋 이후 캐시 쓰기에 실패했다: {}", name, it) }
            }
        })
    }

    private fun encode(key: Any) = "$name:$key"

    private fun expiration() = ttl.plusSeconds(Random.nextLong(1, 10))

    private fun delete(keys: Collection<Any>) {
        redisson.keys.delete(*keys.map(::encode).toTypedArray())
    }
}
