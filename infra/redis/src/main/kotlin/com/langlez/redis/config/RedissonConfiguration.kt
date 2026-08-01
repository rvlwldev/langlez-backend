package com.langlez.redis.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.codec.JsonJacksonCodec
import org.redisson.config.Config
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class RedissonConfiguration(
    private val properties: RedisProperties,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()
        // copy() 필수. objectMapper 는 REST 응답 직렬화에도 쓰이는 공용 빈이라
        // 그대로 activateDefaultTyping 하면 모든 API 응답에 @class 가 붙는다.
        val redisMapper = objectMapper.copy().activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.langlez.")
                .allowIfSubType("java.util.")
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY,
        )
        config.codec = JsonJacksonCodec(redisMapper)

        val prefix = if (properties.ssl.isEnabled) "rediss://" else "redis://"
        val timeout = properties.timeout?.toMillis()?.toInt() ?: 10000

        if (properties.cluster != null && properties.cluster.nodes.isNotEmpty()) {
            val nodes = properties.cluster.nodes.map { "$prefix$it" }.toTypedArray()
            config.useClusterServers()
                .addNodeAddress(*nodes)
                .setConnectTimeout(timeout)
                .setPassword(properties.password.takeIf { !it.isNullOrBlank() })
        } else if (properties.sentinel != null && properties.sentinel.nodes.isNotEmpty()) {
            val nodes = properties.sentinel.nodes.map { "$prefix$it" }.toTypedArray()
            config.useSentinelServers()
                .setMasterName(properties.sentinel.master)
                .addSentinelAddress(*nodes)
                .setConnectTimeout(timeout)
                .setPassword(properties.password.takeIf { !it.isNullOrBlank() })
                .setDatabase(properties.database)
                .setCheckSentinelsList(false)
        } else {
            config.useSingleServer()
                .setAddress("$prefix${properties.host}:${properties.port}")
                .setDatabase(properties.database)
                .setConnectTimeout(timeout)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(10)
                .setPassword(properties.password.takeIf { !it.isNullOrBlank() })
        }

        return Redisson.create(config)
    }
}
