package com.langlez.echo.api.response

import com.langlez.echo.application.PostView
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class EchoPostResponse(
    @field:Schema(description = "글 id. 타임라인 커서로 그대로 쓴다") val id: Long,
    @field:Schema(description = "작성자 회원 id") val authorId: Long,
    @field:Schema(description = "본문") val content: String,
    @field:Schema(description = "첨부 조회용 URL 목록") val mediaUrls: List<String>,
    @field:Schema(description = "좋아요 수") val likeCount: Long,
    @field:Schema(description = "내가 좋아요를 눌렀는지") val liked: Boolean,
    @field:Schema(description = "작성 시각") val createdAt: Instant,
) {
    constructor(view: PostView) : this(
        id = view.id,
        authorId = view.authorId,
        content = view.content,
        mediaUrls = view.mediaUrls,
        likeCount = view.likeCount,
        liked = view.liked,
        createdAt = view.createdAt,
    )
}
