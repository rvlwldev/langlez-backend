package com.langlez.relationship.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 게시글 신고.
 *
 * `sourceType` 을 클라이언트에게 받지 않는다 — 채팅 신고는 chat 모듈이 이벤트로 보내는 경로가
 * 따로 있고, 종류를 열어 두면 아무 종류나 위조해 운영 큐를 흐릴 수 있다.
 */
data class RelationshipReportRequest(
    @field:Schema(description = "신고할 글 id")
    val postId: Long,

    @field:Schema(description = "글 작성자 id")
    val authorId: Long,

    @field:Schema(description = "신고 사유")
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)
