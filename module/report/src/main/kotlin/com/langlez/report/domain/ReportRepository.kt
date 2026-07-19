package com.langlez.report.domain

interface ReportRepository {
    fun save(report: Report): Report

    fun findAll(
        cursor: Long?,
        size: Int,
        sourceType: Report.SourceType?,
        reportedUserId: Long?,
    ): List<Report>

    fun findById(id: Long): Report?
}
