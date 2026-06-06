package com.langlez.observability.config

import com.p6spy.engine.spy.appender.MessageFormattingStrategy
import org.springframework.util.StringUtils

class P6SpyLogFormatter : MessageFormattingStrategy {
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
            "type=MySQL duration_ms=$elapsed command=\"${sql.trim()}\""
        } else {
            ""
        }
}
