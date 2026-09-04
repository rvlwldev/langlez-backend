package com.langlez.moderation.api

import com.langlez.annotation.MemberId
import com.langlez.moderation.api.request.ModerationHandleReportRequest
import com.langlez.moderation.api.request.ModerationSuspendMemberRequest
import com.langlez.moderation.api.response.ModerationReportResponse
import com.langlez.moderation.application.ModerationService
import com.langlez.moderation.domain.Report
import jakarta.validation.Valid
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class ModerationController(private val service: ModerationService) : ModerationAPI {

    @GetMapping("/reports")
    override fun listReports(
        @RequestParam(required = false) status: Report.Status?,
        @RequestParam(required = false) sourceType: Report.SourceType?,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<ModerationReportResponse> =
        service.findReports(status, sourceType, size.coerceIn(1, MAX_SIZE), cursor)
            .map(::ModerationReportResponse)

    @PatchMapping("/reports/{id}")
    override fun handleReport(
        @MemberId actorId: Long,
        @PathVariable id: Long,
        @RequestBody @Valid request: ModerationHandleReportRequest,
    ): ModerationReportResponse =
        ModerationReportResponse(service.handleReport(id, request.status, request.note, actorId))

    @PostMapping("/members/{id}/suspend")
    @ResponseStatus(NO_CONTENT)
    override fun suspendMember(
        @MemberId actorId: Long,
        @PathVariable id: Long,
        @RequestBody @Valid request: ModerationSuspendMemberRequest,
    ) {
        service.suspendMember(id, request.reason, request.days, actorId)
    }

    @DeleteMapping("/members/{id}/suspend")
    @ResponseStatus(NO_CONTENT)
    override fun unsuspendMember(@MemberId actorId: Long, @PathVariable id: Long) {
        service.unsuspendMember(id, actorId)
    }

    private companion object {
        const val DEFAULT_SIZE = 20

        // 상한이 없으면 size=1000000 한 방으로 신고 전체를 긁어간다.
        const val MAX_SIZE = 100
    }
}
