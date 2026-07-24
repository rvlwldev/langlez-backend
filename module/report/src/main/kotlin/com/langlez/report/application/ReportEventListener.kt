package com.langlez.report.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.MessageListener
import com.langlez.report.domain.Report
import com.langlez.report.domain.ReportRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ReportEventListener(
    private val reportRepository: ReportRepository,
    private val mapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @MessageListener(topic = "topic-echo_report", group = "report-service")
    fun onEchoPostReported(payload: String) {
        val event = runCatching { mapper.readValue(payload, EchoReportPayload::class.java) }
            .getOrElse {
                logger.warn("Failed to parse echo report event: {}", it.message)
                throw IllegalArgumentException("Failed to parse echo report event payload", it)
            }

        reportRepository.save(
            Report(
                reporterId = event.reporterId,
                reportedUserId = event.reportedUserId,
                sourceType = Report.SourceType.ECHO_POST,
                sourceId = event.postId,
                reason = event.reason,
                triggerMessageId = null,
            ),
        )
    }

    @Transactional
    @MessageListener(topic = "topic-chat_report", group = "report-service")
    fun onChatUserReported(payload: String) {
        val event = runCatching { mapper.readValue(payload, ChatReportPayload::class.java) }
            .getOrElse {
                logger.warn("Failed to parse chat report event: {}", it.message)
                throw IllegalArgumentException("Failed to parse chat report event payload", it)
            }

        reportRepository.save(
            Report(
                reporterId = event.reporterId,
                reportedUserId = event.reportedUserId,
                sourceType = Report.SourceType.CHAT_USER,
                sourceId = event.roomId,
                reason = event.reason,
                triggerMessageId = event.triggerMessageId,
            ),
        )
    }
}

private data class EchoReportPayload(
    val postId: String,
    val reporterId: Long,
    val reportedUserId: Long,
    val reason: String,
)

private data class ChatReportPayload(
    val roomId: String,
    val reporterId: Long,
    val reportedUserId: Long,
    val reason: String,
    val triggerMessageId: String? = null,
)
