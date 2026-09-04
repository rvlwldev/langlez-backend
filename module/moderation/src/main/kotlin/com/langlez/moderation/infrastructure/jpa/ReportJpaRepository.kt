package com.langlez.moderation.infrastructure.jpa

import com.langlez.moderation.domain.Report
import org.springframework.data.jpa.repository.JpaRepository

interface ReportJpaRepository : JpaRepository<Report, Long>
