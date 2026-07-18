package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.PostReport
import org.springframework.data.jpa.repository.JpaRepository

interface PostReportJpaRepository : JpaRepository<PostReport, Long> {
    fun findByReporterIdAndPostId(reporterId: Long, postId: Long): PostReport?
}
