import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisSentinelConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@org.springframework.context.annotation.Configuration
@EnableConfigurationProperties(RedisProperties::class)
open class RedisConfiguration(
    private val redisProperties: RedisProperties,
) {
    @Bean
    open fun redisConnectionFactory(): RedisConnectionFactory {
        if (redisProperties.sentinel != null && redisProperties.sentinel.nodes.isNotEmpty()) {
            val sentinelConfig =
                RedisSentinelConfiguration(
                    redisProperties.sentinel.master,
                    redisProperties.sentinel.nodes.toSet(),
                ).apply {
                    database = redisProperties.database
                }
            return LettuceConnectionFactory(sentinelConfig)
        } else {
            // Fallback to standalone for local/test
            val standaloneConfig =
                org.springframework.data.redis.connection
                    .RedisStandaloneConfiguration(
                        redisProperties.host,
                        redisProperties.port,
                    ).apply {
                        database = redisProperties.database
                    }
            return LettuceConnectionFactory(standaloneConfig)
        }
    }

    @Bean
    fun redisTemplate(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory

        // Serializer Setup
        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)

        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = jsonSerializer
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = jsonSerializer

        return template
    }
}
