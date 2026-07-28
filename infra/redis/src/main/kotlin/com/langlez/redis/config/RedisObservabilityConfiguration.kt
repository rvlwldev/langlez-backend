package com.langlez.redis.config

import com.langlez.logger.PerformanceLogger
import com.langlez.property.LoggerProperties
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.RedisClient
import io.lettuce.core.cluster.RedisClusterClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.stereotype.Component

@Component
class RedisQueryListenerRegister(
    private val performanceLogger: PerformanceLogger,
    private val properties: LoggerProperties,
    private val redisConnectionFactory: RedisConnectionFactory,
) : ApplicationListener<ApplicationReadyEvent> {

    private val logger = LoggerFactory.getLogger(RedisQueryListenerRegister::class.java)

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        try {
            if (redisConnectionFactory is LettuceConnectionFactory) {
                val client = redisConnectionFactory.nativeClient
                val queryLogger = RedisQueryLogger(performanceLogger, properties)

                when (client) {
                    is RedisClient -> {
                        client.addListener(queryLogger)
                        logger.info("Registered Redis Observability CommandListener (Standalone) successfully.")
                    }

                    is RedisClusterClient -> {
                        client.addListener(queryLogger)
                        logger.info("Registered Redis Observability CommandListener (Cluster) successfully.")
                    }

                    is AbstractRedisClient -> {
                        client.addListener(queryLogger)
                        logger.info("Registered Redis Observability CommandListener (Abstract) successfully.")
                    }

                    else -> {
                        logger.warn(
                            "Redis client is not an instance of AbstractRedisClient (found: {}), skipping listener registration.",
                            client?.javaClass?.name
                        )
                    }
                }
            } else {
                logger.warn("RedisConnectionFactory is not LettuceConnectionFactory, skipping listener registration.")
            }
        } catch (e: Exception) {
            logger.warn("Failed to register Redis CommandListener: {}", e.message, e)
        }
    }
}
