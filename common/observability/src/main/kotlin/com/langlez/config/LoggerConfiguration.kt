package com.langlez.config

import com.langlez.observability.PerformanceLogger
import com.p6spy.engine.common.PreparedStatementInformation
import com.p6spy.engine.common.StatementInformation
import com.p6spy.engine.event.JdbcEventListener
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.StringUtils
import java.sql.SQLException

@Configuration
@EnableConfigurationProperties(LoggerProperties::class)
class LoggerConfiguration {

    @Bean
    fun p6SpyEventListener(logger: PerformanceLogger, properties: LoggerProperties): P6SpyEventListener =
        P6SpyEventListener(logger, properties)

    class P6SpyEventListener(
        private val logger: PerformanceLogger,
        private val properties: LoggerProperties
    ) : JdbcEventListener() {

        private fun log(statement: StatementInformation, elapsedNanos: Long, e: SQLException?) {
            val command = statement.sqlWithValues.replace("\"", "'").replace("\n", " ").trim()
                .also { if (!StringUtils.hasText(it)) return@log }
            val durationMs = elapsedNanos / 1_000_000 // Convert ns to ms

            if (e != null) logger.log(
                type = "MySQL",
                command = "$command (FAILED)",
                durationMs = durationMs,
                thresholdMs = properties.mysql.logThresholdMs,
                warnThresholdMs = properties.mysql.warnThresholdMs,
                params = "error=${e.message}",
            )
            else logger.log(
                type = "MySQL",
                command = command,
                durationMs = durationMs,
                thresholdMs = properties.mysql.logThresholdMs,
                warnThresholdMs = properties.mysql.warnThresholdMs,
            )

        }

        override fun onAfterExecute(
            statement: PreparedStatementInformation,
            elapsedNanos: Long,
            e: SQLException?
        ) = log(statement, elapsedNanos, e)

        override fun onAfterExecute(
            statement: StatementInformation,
            timeElapsedNanos: Long,
            sql: String?,
            e: SQLException?,
        ) = log(statement, timeElapsedNanos, e)

        override fun onAfterExecuteQuery(
            statement: PreparedStatementInformation,
            timeElapsedNanos: Long,
            e: SQLException?,
        ) = log(statement, timeElapsedNanos, e)

        override fun onAfterExecuteQuery(
            statement: StatementInformation,
            timeElapsedNanos: Long,
            sql: String?,
            e: SQLException?,
        ) = log(statement, timeElapsedNanos, e)

        // PreparedStatement update
        override fun onAfterExecuteUpdate(
            statement: PreparedStatementInformation,
            timeElapsedNanos: Long,
            count: Int,
            e: SQLException?,
        ) = log(statement, timeElapsedNanos, e)

        override fun onAfterExecuteUpdate(
            statement: StatementInformation,
            timeElapsedNanos: Long,
            sql: String?,
            count: Int,
            e: SQLException?,
        ) = log(statement, timeElapsedNanos, e)

        override fun onAfterExecuteBatch(
            statement: StatementInformation,
            timeElapsedNanos: Long,
            updateCounts: IntArray?,
            e: SQLException?,
        ) = log(statement, timeElapsedNanos, e)

    }
}
