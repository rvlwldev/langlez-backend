package com.langlez.config

import com.langlez.observability.PerformanceLogger
import com.mongodb.event.CommandFailedEvent
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import com.mongodb.event.CommandSucceededEvent
import java.util.concurrent.TimeUnit

class MongoQueryLogger(
    private val logger: PerformanceLogger,
    private val properties: LoggerProperties
) : CommandListener {
    override fun commandStarted(event: CommandStartedEvent) {
        // 시작할 땐 할 필요 없음
    }

    override fun commandSucceeded(event: CommandSucceededEvent) {
        if (event.commandName == "isMaster" || event.commandName == "hello")
            return

        logger.log(
            type = "MongoDB",
            command = event.commandName,
            durationMs = event.getElapsedTime(TimeUnit.MILLISECONDS),
            thresholdMs = properties.mongo.logThresholdMs,
            warnThresholdMs = properties.mongo.warnThresholdMs,
            params = "requestId=${event.requestId}",
        )
    }

    override fun commandFailed(event: CommandFailedEvent) =
        logger.log(
            type = "MongoDB",
            command = "${event.commandName} (FAILED)",
            durationMs = event.getElapsedTime(TimeUnit.MILLISECONDS),
            thresholdMs = properties.mongo.logThresholdMs,
            warnThresholdMs = properties.mongo.warnThresholdMs,
            params = "error=${event.throwable.message}",
        )
}

