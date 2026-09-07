package com.langlez.redis.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.cache.CacheProvider
import com.langlez.redis.cache.ResilientCacheProvider
import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RedissonClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ResilientCacheConfiguration {

    @Bean
    fun cacheProvider(redisson: RedissonClient, meterRegistry: MeterRegistry, objectMapper: ObjectMapper): CacheProvider =
        ResilientCacheProvider(redisson, meterRegistry, objectMapper)
}