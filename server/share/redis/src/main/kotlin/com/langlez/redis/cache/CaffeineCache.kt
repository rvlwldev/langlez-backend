package com.langlez.redis.cache

import com.github.benmanes.caffeine.cache.Cache as NativeCache

internal class CaffeineCache(
    private val cache: NativeCache<Any, String>,
    private val codec: CacheCodec,
) {

    fun <T : Any> get(key: Any, type: Class<T>): T? =
        cache.getIfPresent(key)?.let { codec.decode(it, type) }

    fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T> =
        cache.getAllPresent(keys)
            .mapNotNull { (key, json) -> codec.decode(json, type)?.let { key to it } }
            .toMap()

    fun put(key: Any, value: Any) = cache.put(key, codec.encode(value))

    fun <T : Any> putMany(entries: Map<out Any, T>) =
        cache.putAll(entries.mapValues { (_, value) -> codec.encode(value) })

    fun putIfAbsent(key: Any, value: Any) {
        cache.asMap().putIfAbsent(key, codec.encode(value))
    }

    fun <T : Any> putManyIfAbsent(entries: Map<out Any, T>) =
        entries.forEach { (key, value) -> putIfAbsent(key, value) }

    fun evict(key: Any) = cache.invalidate(key)

    fun evictMany(keys: Collection<Any>) = cache.invalidateAll(keys)

    fun clear() = cache.invalidateAll()
}
