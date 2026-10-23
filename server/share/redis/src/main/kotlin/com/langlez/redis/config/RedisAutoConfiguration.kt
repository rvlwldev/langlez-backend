package com.langlez.redis.config

import com.langlez.redis.cache.CacheProvider
import com.langlez.redis.lock.DistributedLockAspect
import com.langlez.redis.lock.RedisLockService
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.redisson.config.Config
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.transaction.PlatformTransactionManager

@AutoConfiguration
@ConditionalOnClass(RedissonClient::class)
@EnableConfigurationProperties(RedisConnectionProperties::class)
class RedisAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    fun redissonClient(properties: RedisConnectionProperties): RedissonClient {
        val config = Config().apply { codec = StringCodec.INSTANCE }
        val scheme = if (properties.ssl.enabled) "rediss" else "redis"

        config.useSingleServer()
            .setAddress("$scheme://${properties.host}:${properties.port}")
            .setDatabase(properties.database)
            .setPassword(properties.password?.takeIf { it.isNotBlank() })
            .setConnectTimeout(properties.timeout?.toMillis()?.toInt() ?: 10_000)
            .setConnectionPoolSize(64)
            .setConnectionMinimumIdleSize(10)

        return Redisson.create(config)
    }

    @Bean
    @ConditionalOnMissingBean
    fun redisLockService(redisson: RedissonClient) = RedisLockService(redisson)

    @Bean
    @ConditionalOnMissingBean
    fun distributedLockAspect(
        lockService: RedisLockService,
        transactionManager: ObjectProvider<PlatformTransactionManager>,
    ) = DistributedLockAspect(lockService, transactionManager)

    @Bean
    @ConditionalOnMissingBean
    fun cacheProvider(redisson: RedissonClient, registry: ObjectProvider<MeterRegistry>) =
        CacheProvider(redisson, registry.getIfAvailable { SimpleMeterRegistry() })
}
