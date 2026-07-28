package com.langlez.logger

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PerformanceLogger {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun log(
        type: String,
        command: String,
        durationMs: Long,
        thresholdMs: Long = 0,
        warnThresholdMs: Long = Long.MAX_VALUE,
        params: String? = null,
    ) {
        val payload = arrayOf<Any?>(type, command, durationMs, params ?: "")

        if (durationMs >= warnThresholdMs)
            logger.warn("type={} command={} duration_ms={} params={}", *payload)
        else if (durationMs >= thresholdMs)
            logger.trace("type={} command={} duration_ms={} params={}", *payload)
    }
}