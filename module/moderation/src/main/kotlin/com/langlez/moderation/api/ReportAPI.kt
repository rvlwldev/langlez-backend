package com.langlez.moderation.api

import com.langlez.moderation.api.request.ReportRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Report", description = "신고 API")
interface ReportAPI {

    @Operation(summary = "게시글 신고", description = "같은 글을 다시 신고해도 신고가 중복으로 쌓이지 않는다.")
    fun report(memberId: Long, request: ReportRequest)
}
