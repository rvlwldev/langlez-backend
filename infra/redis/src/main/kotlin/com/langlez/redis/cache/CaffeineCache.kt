package com.langlez.redis.cache

import com.fasterxml.jackson.databind.ObjectMapper
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
 * **값은 JSON 왕복으로 저장한다.** Redis 코덱은 값을 직렬화해 오가므로 매 `get` 이 새 인스턴스를
 * 돌려주지만, 이 폴백은 원래 힙 객체를 그대로 돌려줬다 — 호출자가 받은 값을 수정하면(예: 캐시된
 * JPA 엔티티를 고쳐서 저장하기 전) 다른 스레드가 보는 캐시 값도 같이 바뀌었다. `member` 캐시처럼
 * 가변 엔티티를 담는 캐시가 이 경로를 타면 조용히 데이터가 오염된다. Redis 경로와 동일하게
 * `get`/`getMany` 마다 역직렬화해 항상 새 인스턴스를 돌려준다.
 */
class CaffeineCache(
    private val cache: NativeCache<Any, Any>,
    private val mapper: ObjectMapper,
) : Cache {

    override fun <T : Any> get(key: Any, type: Class<T>): T? =
        (cache.getIfPresent(key) as? String)?.let { mapper.readValue(it, type) }

    override fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T> = cache.getAllPresent(keys)
        .mapNotNull { (key, value) -> (value as? String)?.let { key to mapper.readValue(it, type) } }
        .toMap()

    override fun put(key: Any, value: Any) = cache.put(key, mapper.writeValueAsString(value))

    override fun <T : Any> putMany(entries: Map<out Any, T>) =
        cache.putAll(entries.mapValues { (_, value) -> mapper.writeValueAsString(value) })

    override fun putIfAbsent(key: Any, value: Any) {
        cache.asMap().putIfAbsent(key, mapper.writeValueAsString(value))
    }

    override fun <T : Any> putManyIfAbsent(entries: Map<out Any, T>) =
        entries.forEach { (key, value) -> putIfAbsent(key, value) }

    override fun evict(key: Any) = cache.invalidate(key)

    override fun evictMany(keys: Collection<Any>) = cache.invalidateAll(keys)

}
