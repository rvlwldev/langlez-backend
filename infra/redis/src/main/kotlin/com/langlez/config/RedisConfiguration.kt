package com.langlez.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisSentinelConfiguration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
@EnableConfigurationProperties(RedisProperties::class)
open class RedisConfiguration(private val properties: RedisProperties) {

    @Bean
    open fun redisConnectionFactory(): RedisConnectionFactory {
        if (properties.sentinel != null && properties.sentinel.nodes.isNotEmpty()) {
            val sentinelConfig =
                    RedisSentinelConfiguration(
                                    properties.sentinel.master,
                                    properties.sentinel.nodes.toSet(),
                            )
                            .apply { database = properties.database }

            return LettuceConnectionFactory(sentinelConfig)
        } else {
            // Fallback to standalone for local/test
            val standaloneConfig =
                    RedisStandaloneConfiguration(
                                    properties.host,
                                    properties.port,
                            )
                            .apply { database = properties.database }
            return LettuceConnectionFactory(standaloneConfig)
        }
    }

    @Bean
    fun redisTemplate(
            factory: RedisConnectionFactory,
            mapper: ObjectMapper
    ): RedisTemplate<String, Any> {
        val jsonSerializer = GenericJackson2JsonRedisSerializer(mapper)

        return RedisTemplate<String, Any>().apply {
            connectionFactory = factory
            keySerializer = StringRedisSerializer()
            valueSerializer = jsonSerializer
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = jsonSerializer
        }
    }
}
