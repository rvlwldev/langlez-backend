package com.langlez.echo.api.request

import com.langlez.echo.domain.Comment
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EchoCommentCreateRequest(
    @field:Schema(description = "댓글 본문")
    @field:NotBlank
    @field:Size(max = Comment.MAX_CONTENT_LENGTH)
    val content: String,
)
