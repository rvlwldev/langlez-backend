package com.langlez.observability.bridge.redis

import com.langlez.logger.PerformanceLogger
import com.langlez.logger.config.LoggerProperties
import io.lettuce.core.AbstractRedisClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.stereotype.Component

@Configuration
open class RedisObservabilityConfiguration {
    // Kept empty or for other configs
}

@Component
class RedisQueryListenerRegistrar(
    private val performanceLogger: PerformanceLogger,
    private val properties: LoggerProperties,
    private val redisConnectionFactory: RedisConnectionFactory,
) : ApplicationListener<ApplicationReadyEvent> {
    private val logger = LoggerFactory.getLogger(RedisQueryListenerRegistrar::class.java)

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        try {
            if (redisConnectionFactory is LettuceConnectionFactory) {
                // Ensure the client is initialized (getNativeClient might implicitly initialize or return null)
                // Note: getNativeClient() is nullable in Kotlin if platform type?
                val clientObj = redisConnectionFactory.nativeClient

                if (clientObj is AbstractRedisClient) {
                    clientObj.addListener(RedisQueryLogger(performanceLogger, properties))
                    logger.info("Registered Redis Observability CommandListener successfully.")
                } else {
                    logger.warn(
                        "Redis client is not an instance of AbstractRedisClient (found: {}), skipping listener registration.",
                        clientObj?.javaClass?.name,
                    )
                }
            } else {
                logger.warn("RedisConnectionFactory is not LettuceConnectionFactory, skipping listener registration.")
            }
        } catch (e: Exception) {
            logger.warn("Failed to register Redis CommandListener: {}", e.message, e)
        }
    }
}
