package com.langlez.redis.cache

import com.langlez.core.cache.Cache
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import kotlin.random.Random

class RedisCache(
    private val name: String,
    private val redisson: RedissonClient,
    private val ttl: Duration = Duration.ofMinutes(10),
) : Cache {

    override fun <T : Any> get(key: Any, type: Class<T>): T? =
        redisson.getBucket<T>(encode(key)).get()
            .let { value -> if (type.isInstance(value)) type.cast(value) else null }

    override fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T> {
        if (keys.isEmpty()) return emptyMap()

        val map = redisson.buckets
            .get<T>(*keys.map(::encode).toTypedArray()) // MGET 1회
            ?: return emptyMap()

        return keys.asSequence().mapNotNull { key ->
            val value = map[encode(key)]
            if (type.isInstance(value)) key to type.cast(value) else null
        }.toMap()
    }

    override fun put(key: Any, value: Any) = write(listOf(key)) {
        redisson.getBucket<Any>(encode(key)).set(value, expiration())
    }

    override fun <T : Any> putMany(entries: Map<out Any, T>) {
        if (entries.isEmpty()) return

        write(entries.keys) {
            // MSET은 TTL이 안되서 RESP 프로토콜로 Pipelining
            val batch = redisson.createBatch()

            entries.forEach { (key, value) ->
                batch.getBucket<Any>(encode(key)).setAsync(value, expiration())
            }

            batch.execute()
        }
    }

    override fun evict(key: Any) = evictMany(listOf(key))

    override fun evictMany(keys: Collection<Any>) {
        if (keys.isEmpty()) return
        write(keys) { delete(keys) }
    }

    /**
     * 트랜잭션 시 커밋전에 캐시 갱신 방지
     */
    private fun write(keys: Collection<Any>, commit: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return commit()
        }

        delete(keys)

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            // afterCommit 은 ResilientCache 의 runGuarded 밖에서 돈다. 여기서 예외를 흘리면
            // 이미 커밋된 요청이 Redis 블립 하나로 500 이 된다. 캐시 갱신 실패는 미스일 뿐이라 삼킨다.
            override fun afterCommit() {
                runCatching(commit).onFailure {
                    LoggerFactory.getLogger(RedisCache::class.java)
                        .warn("Cache write after commit failed for cache: {}", name, it)
                }
            }
        })
    }

    private fun encode(key: Any) = "$name:$key"
    private fun expiration() = ttl.plusSeconds(Random.nextLong(1, 10))
    private fun delete(keys: Collection<Any>) {
        redisson.keys.delete(*keys.map(::encode).toTypedArray())
    }

}