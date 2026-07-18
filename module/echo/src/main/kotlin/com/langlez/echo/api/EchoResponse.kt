package com.langlez.echo.api

import com.langlez.echo.domain.PostMedia
import java.time.Instant

class EchoResponse {
    data class MemberSummary(
        val username: String,
        val nickname: String
    )

    data class PostMediaDto(
        val url: String,
        val type: PostMedia.Type
    )

    data class PostDto(
        val postId: Long,
        val username: String,
        val nickname: String,
        val content: String,
        val media: List<PostMediaDto>,
        val likeCount: Long,
        val createdAt: Instant
    )

    data class CursorList(
        val nextCursor: Long?,
        val posts: List<PostDto>
    )

    data class CommentDto(
        val commentId: Long,
        val username: String,
        val nickname: String,
        val content: String,
        val createdAt: Instant
    )

    data class CommentCursorList(
        val nextCursor: Long?,
        val comments: List<CommentDto>
    )

    data class TrendingHashtag(
        val hashtag: String,
        val count: Long
    )
}
