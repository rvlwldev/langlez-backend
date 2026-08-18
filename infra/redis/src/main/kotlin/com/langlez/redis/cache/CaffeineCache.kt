package com.langlez.redis.cache

import com.langlez.core.cache.Cache
import com.github.benmanes.caffeine.cache.Cache as NativeCache

/**
 * 로컬 폴백 캐시. `ResilientCache` 가 Redis 실패 시에만 태운다.
 *
 * 인스턴스가 캐시 이름당 하나씩 만들어지므로 키 네임스페이스가 겹치지 않는다.
 * Redis 어댑터와 달리 키를 인코딩하지 않고 객체 그대로 쓴다.
 * 따라서 로컬은 `equals`/`hashCode`, Redis 는 `toString` 으로 키를 가른다.
 * `Long` / `String` / enum / data class 만 키로 쓰면 두 규칙이 일치한다.
 *
 * 인메모리라 왕복이 없으므로 multi 연산은 루프로 충분하고,
 * 폴백 전용이라 트랜잭션 지연도 필요 없다.
 */
class CaffeineCache(private val cache: NativeCache<Any, Any>) : Cache {

    override fun <T : Any> get(key: Any, type: Class<T>): T? = cache.getIfPresent(key)
        .let { value -> if (type.isInstance(value)) type.cast(value) else null }

    override fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T> = cache.getAllPresent(keys)
        .mapNotNull { (key, value) -> if (type.isInstance(value)) key to type.cast(value) else null }
        .toMap()

    override fun put(key: Any, value: Any) = cache.put(key, value)

    override fun <T : Any> putMany(entries: Map<out Any, T>) = cache.putAll(entries)

    override fun evict(key: Any) = cache.invalidate(key)

    override fun evictMany(keys: Collection<Any>) = cache.invalidateAll(keys)

}
