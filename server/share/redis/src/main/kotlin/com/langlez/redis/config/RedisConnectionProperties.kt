package com.langlez.redis.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("spring.data.redis")
data class RedisConnectionProperties(
    val host: String = "localhost",
    val port: Int = 6379,
    val database: Int = 0,
    val password: String? = null,
    val timeout: Duration? = null,
    val ssl: Ssl = Ssl(),
) {
    data class Ssl(val enabled: Boolean = false)
}
