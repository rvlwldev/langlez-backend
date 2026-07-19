package com.langlez.report.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "reports",
    indexes = [
        Index(name = "IDX_REPORT_SOURCE", columnList = "source_type, source_id, created_at"),
        Index(name = "IDX_REPORT_REPORTED_USER", columnList = "reported_user_id, created_at"),
        Index(name = "IDX_REPORT_REPORTER", columnList = "reporter_id, created_at"),
    ],
)
class Report(
    val reporterId: Long,
    val reportedUserId: Long,
    @Enumerated(EnumType.STRING) val sourceType: SourceType,
    val sourceId: String, // ECHO_POST면 postId, CHAT_USER면 roomId
    @Column(columnDefinition = "TEXT") val reason: String,
    val triggerMessageId: String? = null, // CHAT_USER 신고에서만 사용, "이 메시지 이후 신고됨"
    val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    enum class SourceType { ECHO_POST, CHAT_USER }
}
