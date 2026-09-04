package com.langlez.moderation.api.request

import com.langlez.moderation.domain.Report
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ModerationHandleReportRequest(
    @field:Schema(description = "바꿀 상태. RECEIVED 로는 되돌릴 수 없다.", example = "ACTIONED")
    @field:NotNull
    val status: Report.Status,

    @field:Schema(description = "운영자 메모. 생략하면 기존 메모를 그대로 둔다.", nullable = true)
    @field:Size(max = 2000)
    val note: String? = null,
)
