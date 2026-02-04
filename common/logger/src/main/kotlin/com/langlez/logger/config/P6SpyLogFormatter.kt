package com.langlez.logger.config

import com.p6spy.engine.spy.appender.MessageFormattingStrategy
import org.springframework.util.StringUtils

class P6SpyLogFormatter : MessageFormattingStrategy {
    // We only care about the effective SQL and duration.
    // The actual logging happens via the P6Spy logger configuration, but we want to format it
    // such that our JSON encoder or PerformanceLogger logic picks it up cleanly.
    // Actually, P6Spy dumps this string to its logger.
    // To strictly follow our schema: type=MySQL command=[sql] duration_ms=[elapsed]
    override fun formatMessage(
        connectionId: Int,
        now: String,
        elapsed: Long,
        category: String,
        prepared: String,
        sql: String,
        url: String,
    ): String =
        if (StringUtils.hasText(sql)) {
            val command =
                sql
                    .replace("\"", "'")
                    .replace("\n", " ")
                    .trim()

            "type=MySQL command=\"${command}\" duration_ms=$elapsed"
        } else {
            ""
        }
}
