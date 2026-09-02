package com.langlez.report.domain

/** 신고 저장소 포트. */
interface ReportRepository {

    fun save(report: Report): Report

    /**
     * 같은 신고가 이미 있는지.
     *
     * 카프카는 at-least-once 라 같은 신고 이벤트가 두 번 배달된다. 이벤트에 고유 id 가 없어
     * (신고자, 출처 종류, 출처 id, 트리거 메시지) 조합을 신고 하나의 식별자로 본다.
     */
    fun exists(
        reporterId: Long,
        sourceType: Report.SourceType,
        sourceId: String,
        triggerMessageId: String?,
    ): Boolean
}
