package com.langlez.redis.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisClusterConfiguration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisSentinelConfiguration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class LettuceConfiguration(private val properties: RedisProperties) {

    @Bean
    fun redisConnectionFactory() =
        LettuceConnectionFactory(
            when {
                // cluster
                properties.cluster != null && properties.cluster.nodes.isNotEmpty() -> {
                    RedisClusterConfiguration(properties.cluster.nodes)
                }

                // sentinel
                properties.sentinel != null && properties.sentinel.nodes.isNotEmpty() -> {
                    RedisSentinelConfiguration(
                        properties.sentinel.master,
                        properties.sentinel.nodes.toSet()
                    )
                        .apply { database = properties.database }
                }

                // standalone (fallback)
                else -> {
                    RedisStandaloneConfiguration(properties.host, properties.port).apply {
                        database = properties.database
                    }
                }
            }
        )

    @Bean
    fun template(factory: RedisConnectionFactory, mapper: ObjectMapper) =
        RedisTemplate<String, Any>().apply {
            val jsonSerializer = GenericJackson2JsonRedisSerializer(mapper)

            connectionFactory = factory
            keySerializer = StringRedisSerializer()
            valueSerializer = jsonSerializer
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = jsonSerializer
        }
}
