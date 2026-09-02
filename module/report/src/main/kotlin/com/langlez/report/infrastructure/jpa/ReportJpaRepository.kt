package com.langlez.report.infrastructure.jpa

import com.langlez.report.domain.Report
import org.springframework.data.jpa.repository.JpaRepository

interface ReportJpaRepository : JpaRepository<Report, Long>
