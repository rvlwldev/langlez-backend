package com.langlez.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "logger")
data class LoggerProperties(
    val mysql: Thresholds = Thresholds(),
    val mongo: Thresholds = Thresholds(),
    val redis: Thresholds = Thresholds(),
) {
    data class Thresholds(
        val warnThresholdMs: Long = Long.MAX_VALUE, // 경고(WARN) 로깅 밀리초 임계값.
        val logThresholdMs: Long = 0, // INFO 레벨 로깅 밀리초 임계값.
    )
}
