package com.langlez.moderation.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * 회원 정지 요청.
 *
 * 대상 회원은 경로에서, 조치자는 인증(`@MemberId`)에서 온다. 본문으로 받지 않는다 —
 * 조치자를 본문으로 받으면 감사 기록을 위조할 수 있다.
 */
data class ModerationSuspendMemberRequest(
    @field:Schema(description = "정지 사유", nullable = true)
    @field:Size(max = 500)
    val reason: String? = null,

    @field:Schema(description = "정지 기간(일). 생략하면 무기한이고 사람이 직접 풀어야 한다.", nullable = true)
    @field:Positive
    val days: Long? = null,
)
