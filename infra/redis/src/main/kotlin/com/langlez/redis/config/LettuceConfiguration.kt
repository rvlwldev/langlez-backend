package com.langlez.redis.config

import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisClusterConfiguration
import org.springframework.data.redis.connection.RedisSentinelConfiguration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class LettuceConfiguration(private val properties: RedisProperties) {

    @Bean
    fun redisConnectionFactory() =
        LettuceConnectionFactory(
            when {
                properties.cluster != null && properties.cluster.nodes.isNotEmpty() ->
                    RedisClusterConfiguration(properties.cluster.nodes)

                properties.sentinel != null && properties.sentinel.nodes.isNotEmpty() ->
                    RedisSentinelConfiguration(
                        properties.sentinel.master,
                        properties.sentinel.nodes.toSet()
                    ).apply { database = properties.database }

                else ->
                    RedisStandaloneConfiguration(properties.host, properties.port).apply {
                        database = properties.database
                    }
            }
        )
}
