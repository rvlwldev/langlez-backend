package com.langlez.core.cache

interface CacheProvider {
    fun getCache(name: String): Cache
}
