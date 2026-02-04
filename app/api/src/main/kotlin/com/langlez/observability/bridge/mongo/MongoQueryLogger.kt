package com.langlez.observability.bridge.mongo

import com.langlez.logger.PerformanceLogger
import com.langlez.logger.config.LoggerProperties
import com.mongodb.event.CommandFailedEvent
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import com.mongodb.event.CommandSucceededEvent
import java.util.concurrent.TimeUnit

class MongoQueryLogger(
    private val performanceLogger: PerformanceLogger,
    private val properties: LoggerProperties,
) : CommandListener {
    override fun commandStarted(event: CommandStartedEvent) {
        // We could log start, but usually we care about completion & duration.
    }

    override fun commandSucceeded(event: CommandSucceededEvent) {
        val durationMs = event.getElapsedTime(TimeUnit.MILLISECONDS)
        // Filter out internal commands if needed, e.g., "isMaster", "hello"
        if (event.commandName == "isMaster" || event.commandName == "hello") return

        performanceLogger.log(
            type = "MongoDB",
            command = event.commandName,
            durationMs = durationMs,
            thresholdMs = properties.mongo.logThresholdMs,
            warnThresholdMs = properties.mongo.warnThresholdMs,
            params = "requestId=${event.requestId}",
        )
    }

    override fun commandFailed(event: CommandFailedEvent) {
        val durationMs = event.getElapsedTime(TimeUnit.MILLISECONDS)
        performanceLogger.log(
            type = "MongoDB",
            command = "${event.commandName} (FAILED)",
            durationMs = durationMs,
            thresholdMs = properties.mongo.logThresholdMs,
            warnThresholdMs = properties.mongo.warnThresholdMs,
            params = "error=${event.throwable.message}",
        )
    }
}
