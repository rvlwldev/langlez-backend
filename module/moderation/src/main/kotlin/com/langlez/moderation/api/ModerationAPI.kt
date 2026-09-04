package com.langlez.moderation.api

import com.langlez.moderation.api.request.ModerationHandleReportRequest
import com.langlez.moderation.api.request.ModerationSuspendMemberRequest
import com.langlez.moderation.api.response.ModerationReportResponse
import com.langlez.moderation.domain.Report
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * 운영자 전용. `/api/v1/admin` 하위 전부는 `WebSecurityConfiguration` 이 URL 접두사로 `ROLE_ADMIN` 을 요구한다.
 *
 * `@PreAuthorize` 를 쓰지 않는다 — 어노테이션을 빠뜨린 메서드가 조용히 열린 채 남는다.
 * 접두사로 막으면 이 컨트롤러에 메서드를 추가하는 것만으로 인가가 따라온다.
 */
@Tag(name = "Moderation", description = "운영자 전용 신고 처리·회원 제재 API")
interface ModerationAPI {

    @Operation(
        summary = "신고 목록 조회",
        description = "최신순. status/sourceType 은 생략하면 전체다. cursor 는 직전 페이지 마지막 신고 id. " +
            "신고 원본(글 본문·채팅 메시지)은 담기지 않는다 — 메타데이터만 준다.",
    )
    fun listReports(
        @Parameter(description = "처리 상태 필터") status: Report.Status?,
        @Parameter(description = "신고 대상 종류 필터") sourceType: Report.SourceType?,
        @Parameter(description = "페이지 크기(최대 100)") size: Int,
        @Parameter(description = "직전 페이지 마지막 신고 id") cursor: Long?,
    ): List<ModerationReportResponse>

    @Operation(
        summary = "신고 처리",
        description = "상태를 바꾸고 처리자·처리 시각을 남긴다. RECEIVED 로는 되돌릴 수 없다(400). " +
            "회원 제재는 별도 호출이다 — 한 호출로 묶으면 반쪽 상태가 생긴다.",
    )
    fun handleReport(
        actorId: Long,
        @Parameter(description = "신고 id") id: Long,
        request: ModerationHandleReportRequest,
    ): ModerationReportResponse

    @Operation(
        summary = "회원 정지",
        description = "days 를 생략하면 무기한이다. 자기 자신은 400, 다른 운영자는 403, 탈퇴 회원은 400 이다.",
    )
    fun suspendMember(
        actorId: Long,
        @Parameter(description = "정지할 회원 id") id: Long,
        request: ModerationSuspendMemberRequest,
    )

    @Operation(summary = "회원 정지 해제", description = "정지 상태가 아니면 400. 열려 있던 정지 이력도 함께 닫는다.")
    fun unsuspendMember(actorId: Long, @Parameter(description = "정지를 풀 회원 id") id: Long)
}
