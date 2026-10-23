package com.langlez.redis.cache

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

internal class CacheCodec {

    private val mapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    fun encode(value: Any): String = mapper.writeValueAsString(value)

    fun <T : Any> decode(json: String, type: Class<T>): T? =
        runCatching { mapper.readValue(json, type) }.getOrNull()
}
