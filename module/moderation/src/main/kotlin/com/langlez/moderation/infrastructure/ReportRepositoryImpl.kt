package com.langlez.moderation.infrastructure

import com.langlez.moderation.domain.Report
import com.langlez.moderation.domain.ReportRepository
import com.langlez.moderation.infrastructure.jpa.ReportJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import com.langlez.moderation.domain.QReport.Companion.report as QReport

/** 캐시를 두지 않는다. 신고는 쓰기 위주고 읽기는 존재 확인뿐이다. */
@Repository
class ReportRepositoryImpl(
    private val jpa: ReportJpaRepository,
    private val dsl: JPAQueryFactory,
) : ReportRepository {

    override fun save(report: Report): Report = jpa.save(report)

    override fun exists(
        reporterId: Long,
        sourceType: Report.SourceType,
        sourceId: String,
        triggerMessageId: String?,
    ): Boolean = dsl.selectOne()
        .from(QReport)
        .where(
            QReport.reporterId.eq(reporterId),
            QReport.sourceType.eq(sourceType),
            QReport.sourceId.eq(sourceId),
            // 트리거 메시지가 없는 신고는 NULL 로 저장된다. eq(null) 은 항상 false 라 IS NULL 로 비교해야 한다.
            triggerMessageId?.let(QReport.triggerMessageId::eq) ?: QReport.triggerMessageId.isNull,
        )
        .fetchFirst() != null
}
