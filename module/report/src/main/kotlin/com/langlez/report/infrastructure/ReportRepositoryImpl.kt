package com.langlez.report.infrastructure

import com.langlez.report.domain.Report
import com.langlez.report.domain.ReportRepository
import com.langlez.report.infrastructure.jpa.ReportJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ReportRepositoryImpl(
    private val jpa: ReportJpaRepository,
) : ReportRepository {

    override fun save(report: Report): Report = jpa.save(report)

    override fun findAll(
        cursor: Long?,
        size: Int,
        sourceType: Report.SourceType?,
        reportedUserId: Long?,
    ): List<Report> =
        jpa.findAllFiltered(cursor, sourceType, reportedUserId, PageRequest.of(0, size))

    override fun findById(id: Long): Report? = jpa.findByIdOrNull(id)
}
