package com.langlez.relationship.infrastructure.jpa

import com.langlez.relationship.domain.Report
import org.springframework.data.jpa.repository.JpaRepository

interface ReportJpaRepository : JpaRepository<Report, Long>
