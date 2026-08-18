package com.langlez.core.cache

interface Cache {
    fun <T : Any> get(key: Any, type: Class<T>): T?
    fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T>

    fun put(key: Any, value: Any)
    fun <T : Any> putMany(entries: Map<out Any, T>)

    fun evict(key: Any)
    fun evictMany(keys: Collection<Any>)
}

inline fun <reified T : Any> Cache.get(key: Any): T? =
    get(key, T::class.javaObjectType)

inline fun <reified T : Any> Cache.getMany(keys: Collection<Any>): Map<Any, T> =
    getMany(keys, T::class.javaObjectType)
