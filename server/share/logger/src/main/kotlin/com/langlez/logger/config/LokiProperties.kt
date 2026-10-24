package com.langlez.logger.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("langlez.logger.loki")
data class LokiProperties(
    val pushUrl: String,
    val username: String? = null,
    val password: String? = null,
    val batchTimeout: Duration = Duration.ofSeconds(1),
)
