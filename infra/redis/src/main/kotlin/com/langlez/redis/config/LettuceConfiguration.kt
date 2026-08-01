package com.langlez.redis.config

import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

/**
 * Lettuce 커넥션은 쿼리 옵저버빌리티(`RedisQueryListenerRegister`)와 구 `RedisCacheManager` 만 쓴다.
 * 애플리케이션 로직의 Redis 접근은 Redisson 이 담당한다.
 */
@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class LettuceConfiguration(private val properties: RedisProperties) {

    @Bean
    fun redisConnectionFactory() = LettuceConnectionFactory(
        RedisStandaloneConfiguration(properties.host, properties.port)
            .apply { database = properties.database }
    )
}
