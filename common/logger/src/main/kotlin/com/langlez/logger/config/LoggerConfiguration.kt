package com.langlez.logger.config

import com.langlez.logger.PerformanceLogger
import com.p6spy.engine.common.PreparedStatementInformation
import com.p6spy.engine.common.StatementInformation
import com.p6spy.engine.event.JdbcEventListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.StringUtils
import java.sql.SQLException

// @EnableConfigurationProperties(ObservabilityProperties::class)
@Configuration
@org.springframework.boot.context.properties.EnableConfigurationProperties(LoggerProperties::class)
class LoggerConfiguration {

    @Bean
    fun p6SpyEventListener(
        logger: PerformanceLogger,
        properties: LoggerProperties,
    ): P6SpyEventListener = P6SpyEventListener(logger, properties)

    class P6SpyEventListener(
        private val logger: PerformanceLogger,
        private val properties: LoggerProperties,
    ) : JdbcEventListener() {
        // PreparedStatement execution
        override fun onAfterExecute(
            statementInformation: PreparedStatementInformation,
            timeElapsedNanos: Long,
            e: SQLException?,
        ) {
            log(statementInformation, timeElapsedNanos, e)
        }

        // Statement execution
        override fun onAfterExecute(
            statementInformation: StatementInformation,
            timeElapsedNanos: Long,
            sql: String?,
            e: SQLException?,
        ) {
            log(statementInformation, timeElapsedNanos, e)
        }

        // PreparedStatement query
        override fun onAfterExecuteQuery(
            statementInformation: PreparedStatementInformation,
            timeElapsedNanos: Long,
            e: SQLException?,
        ) {
            log(statementInformation, timeElapsedNanos, e)
        }

        // Statement query
        override fun onAfterExecuteQuery(
            statementInformation: StatementInformation,
            timeElapsedNanos: Long,
            sql: String?,
            e: SQLException?,
        ) {
            log(statementInformation, timeElapsedNanos, e)
        }

        // PreparedStatement update
        override fun onAfterExecuteUpdate(
            statementInformation: PreparedStatementInformation,
            timeElapsedNanos: Long,
            count: Int,
            e: SQLException?,
        ) {
            log(statementInformation, timeElapsedNanos, e)
        }

        // Statement update
        override fun onAfterExecuteUpdate(
            statementInformation: StatementInformation,
            timeElapsedNanos: Long,
            sql: String?,
            count: Int,
            e: SQLException?,
        ) {
            log(statementInformation, timeElapsedNanos, e)
        }

        // Batch execution
        override fun onAfterExecuteBatch(
            statementInformation: StatementInformation,
            timeElapsedNanos: Long,
            updateCounts: IntArray?,
            e: SQLException?,
        ) {
            log(statementInformation, timeElapsedNanos, e)
        }

        private fun log(
            statementInformation: StatementInformation,
            timeElapsedNanos: Long,
            e: SQLException?,
        ) {
            val sql = statementInformation.sqlWithValues
            if (!StringUtils.hasText(sql)) return

            val durationMs = timeElapsedNanos / 1_000_000 // Convert ns to ms

            val command = sql.replace("\"", "'").replace("\n", " ").trim()

            if (e != null) {
                logger.log(
                    type = "MySQL",
                    command = "$command (FAILED)",
                    durationMs = durationMs,
                    thresholdMs = properties.mysql.logThresholdMs,
                    warnThresholdMs = properties.mysql.warnThresholdMs,
                    params = "error=${e.message}",
                )
            } else {
                logger.log(
                    type = "MySQL",
                    command = command,
                    durationMs = durationMs,
                    thresholdMs = properties.mysql.logThresholdMs,
                    warnThresholdMs = properties.mysql.warnThresholdMs,
                )
            }
        }
    }
}
