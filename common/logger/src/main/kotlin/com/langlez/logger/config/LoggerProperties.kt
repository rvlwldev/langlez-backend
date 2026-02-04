package com.langlez.logger.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "logger")
data class LoggerProperties(
    val mysql: Thresholds = Thresholds(),
    val mongo: Thresholds = Thresholds(),
    val redis: Thresholds = Thresholds(),
) {
    data class Thresholds(
        /**
         * Duration threshold in milliseconds to log at WARN level.
         */
        val warnThresholdMs: Long = Long.MAX_VALUE,
        /**
         * Duration threshold in milliseconds to log at INFO level.
         * Operations faster than this will not be logged.
         */
        val logThresholdMs: Long = 0,
    )
}
