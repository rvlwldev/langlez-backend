package com.langlez.moderation.domain

/** 신고 저장소 포트. */
interface ReportRepository {

    fun save(report: Report): Report

    /** 없으면 null. 예외 변환은 application 몫이다. */
    fun find(id: Long): Report?

    /**
     * 운영 목록. [status] / [sourceType] 이 null 이면 그 조건을 걸지 않는다.
     *
     * 정렬·커서는 `created_at` 이 아니라 id 다. 인스턴스 간 시계 차이로 같은 시각이 겹치면
     * 페이지가 새거나 겹친다.
     */
    fun findAll(
        status: Report.Status?,
        sourceType: Report.SourceType?,
        size: Int,
        cursor: Long?,
    ): List<Report>

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
