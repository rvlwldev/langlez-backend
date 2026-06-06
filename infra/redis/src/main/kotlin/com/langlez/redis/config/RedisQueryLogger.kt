package com.langlez.redis.config
import com.langlez.observability.config.LoggerProperties

import com.langlez.observability.PerformanceLogger
import io.lettuce.core.event.command.CommandFailedEvent
import io.lettuce.core.event.command.CommandListener
import io.lettuce.core.event.command.CommandStartedEvent
import io.lettuce.core.event.command.CommandSucceededEvent
import io.lettuce.core.protocol.CommandArgs

class RedisQueryLogger(
    private val performanceLogger: PerformanceLogger,
    private val properties: LoggerProperties,
) : CommandListener {
    override fun commandStarted(event: CommandStartedEvent) {
        // No-op
    }

    override fun commandSucceeded(event: CommandSucceededEvent) {
        val command = event.command
        val durationMs = event.duration.toMillis() // event.duration is a java.time.Duration

        val type = command.type.toString()
        val args = extractArgs(command.args)

        performanceLogger.log(
            type = "Redis",
            command = "$type $args",
            durationMs = durationMs,
            thresholdMs = properties.redis.logThresholdMs,
            warnThresholdMs = properties.redis.warnThresholdMs,
        )
    }

    override fun commandFailed(event: CommandFailedEvent) {
        val command = event.command
        val type = command.type.toString()
        val args = extractArgs(command.args)

        performanceLogger.log(
            type = "Redis",
            command = "$type $args (FAILED)",
            durationMs = -1L,
            thresholdMs = 0,
            warnThresholdMs = 0,
            params = "error=${event.cause.message}",
        )
    }

    private fun extractArgs(args: CommandArgs<*, *>?): String = args?.toCommandString() ?: ""
}