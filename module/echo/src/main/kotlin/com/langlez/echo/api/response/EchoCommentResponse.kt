package com.langlez.echo.api.response

import com.langlez.echo.domain.Comment
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class EchoCommentResponse(
    @field:Schema(description = "댓글 id. 목록 커서로 그대로 쓴다") val id: Long,
    @field:Schema(description = "글 id") val postId: Long,
    @field:Schema(description = "작성자 회원 id") val authorId: Long,
    @field:Schema(description = "본문") val content: String,
    @field:Schema(description = "작성 시각") val createdAt: Instant,
) {
    constructor(comment: Comment) : this(
        id = comment.id,
        postId = comment.postId,
        authorId = comment.authorId,
        content = comment.content,
        createdAt = comment.createdAt,
    )
}
