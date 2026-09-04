package com.langlez.moderation.api.response

import com.langlez.moderation.domain.Report
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * 운영자용 신고 항목.
 *
 * **신고 원본(게시글 본문·채팅 메시지)은 담지 않는다.** `Report` 가 가진 건 `sourceType` 과
 * 문자열 `sourceId` 뿐이라, 원본을 붙이려면 echo·chat 에 조회 포트를 새로 내야 한다.
 * 이번 범위는 메타데이터로 목록·분류·조치까지다.
 */
data class ModerationReportResponse(
    @field:Schema(description = "신고 id") val id: Long,
    @field:Schema(description = "신고자 id") val reporterId: Long,
    @field:Schema(description = "피신고자 id") val reportedUserId: Long,
    @field:Schema(description = "신고 대상 종류") val sourceType: Report.SourceType,
    @field:Schema(description = "ECHO_POST 면 글 id, CHAT_USER 면 방 id") val sourceId: String,
    @field:Schema(description = "신고 사유") val reason: String,
    @field:Schema(description = "채팅 신고에서 집은 메시지 id", nullable = true) val triggerMessageId: String?,
    @field:Schema(description = "처리 상태") val status: Report.Status,
    @field:Schema(description = "운영자 메모", nullable = true) val adminNote: String?,
    @field:Schema(description = "마지막으로 처리한 운영자 id", nullable = true) val handledBy: Long?,
    @field:Schema(description = "마지막 처리 시각", nullable = true) val handledAt: Instant?,
    @field:Schema(description = "접수 시각") val createdAt: Instant,
) {
    constructor(report: Report) : this(
        id = report.id,
        reporterId = report.reporterId,
        reportedUserId = report.reportedUserId,
        sourceType = report.sourceType,
        sourceId = report.sourceId,
        reason = report.reason,
        triggerMessageId = report.triggerMessageId,
        status = report.status,
        adminNote = report.adminNote,
        handledBy = report.handledBy,
        handledAt = report.handledAt,
        createdAt = report.createdAt,
    )
}
