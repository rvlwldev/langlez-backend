package com.langlez.report.infrastructure.jpa

import com.langlez.report.domain.Report
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ReportJpaRepository : JpaRepository<Report, Long> {

    @Query(
        """
        SELECT r FROM Report r
        WHERE (:cursor IS NULL OR r.id < :cursor)
          AND (:sourceType IS NULL OR r.sourceType = :sourceType)
          AND (:reportedUserId IS NULL OR r.reportedUserId = :reportedUserId)
        ORDER BY r.id DESC
        """,
    )
    fun findAllFiltered(
        cursor: Long?,
        sourceType: Report.SourceType?,
        reportedUserId: Long?,
        pageable: Pageable,
    ): List<Report>
}
