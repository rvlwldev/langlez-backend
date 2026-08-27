package com.langlez.core.cache

interface Cache {
    fun <T : Any> get(key: Any, type: Class<T>): T?
    fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T>

    fun put(key: Any, value: Any)
    fun <T : Any> putMany(entries: Map<out Any, T>)

    /**
     * 이미 값이 있으면 덮어쓰지 않는다. **read-through(캐시 미스로 DB 에서 읽어온 값) 적재 전용**이다.
     *
     * 쓰기 트랜잭션이 커밋되기 전에 DB 를 읽은 요청은 낡은 값을 손에 쥔 채로 캐시 적재까지
     * 임의로 지연될 수 있다. 그 적재가 커밋 후 갱신보다 늦게 도착하면 최종 상태를 덮어쓴다.
     * (실제로 정지된 회원이 캐시의 ACTIVE 로 계속 통과하는 사고가 여기서 났다.)
     * read-through 는 "비어 있으면 채운다"만 하고, 덮어쓰기는 쓰기 경로([put])에만 허용한다.
     */
    fun putIfAbsent(key: Any, value: Any)
    fun <T : Any> putManyIfAbsent(entries: Map<out Any, T>)

    fun evict(key: Any)
    fun evictMany(keys: Collection<Any>)
}

inline fun <reified T : Any> Cache.get(key: Any): T? =
    get(key, T::class.javaObjectType)

inline fun <reified T : Any> Cache.getMany(keys: Collection<Any>): Map<Any, T> =
    getMany(keys, T::class.javaObjectType)
