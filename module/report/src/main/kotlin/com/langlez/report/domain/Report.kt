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
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "reports",
    // 같은 신고를 두 행으로 만들지 않는다. 구성은 ReportRepository.exists 의 조건과 같다.
    // **실제 DDL 은 V7 이 만든다** — 그쪽은 `nulls not distinct` 라 트리거 메시지가 NULL 인
    // 신고(게시글 신고 전부)도 막지만, JPA 의 UniqueConstraint 로는 그 의미를 표현할 방법이 없다.
    // validate 는 유니크·인덱스를 보지 않으니 여기 선언은 문서 역할이다. 이걸로 DDL 을 만들지 마라.
    uniqueConstraints = [
        UniqueConstraint(
            name = "UNQ_REPORT_IDENTITY",
            columnNames = ["reporter_id", "source_type", "source_id", "trigger_message_id"],
        ),
    ],
    indexes = [
        Index(name = "IDX_REPORT_SOURCE", columnList = "source_type, id"),
        Index(name = "IDX_REPORT_REPORTED_USER", columnList = "reported_user_id, id"),
        Index(name = "IDX_REPORT_REPORTER", columnList = "reporter_id, id"),
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
    var id: Long = 0

    enum class SourceType { ECHO_POST, CHAT_USER }
}
