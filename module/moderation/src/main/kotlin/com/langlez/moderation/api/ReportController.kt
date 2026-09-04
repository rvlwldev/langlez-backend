package com.langlez.moderation.api

import com.langlez.annotation.MemberId
import com.langlez.moderation.api.request.ReportRequest
import com.langlez.moderation.application.ReportService
import com.langlez.moderation.domain.Report
import jakarta.validation.Valid
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reports")
class ReportController(private val service: ReportService) : ReportAPI {

    @PostMapping
    @ResponseStatus(NO_CONTENT)
    override fun report(@MemberId memberId: Long, @RequestBody @Valid request: ReportRequest) {
        service.report(
            reporterId = memberId,
            reportedUserId = request.authorId,
            sourceType = Report.SourceType.ECHO_POST,
            sourceId = request.postId.toString(),
            reason = request.reason,
        )
    }
}
