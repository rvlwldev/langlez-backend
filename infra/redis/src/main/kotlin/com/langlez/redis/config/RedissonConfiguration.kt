package com.langlez.redis.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class RedissonConfiguration(private val properties: RedisProperties) {

    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()
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
