package com.langlez.echo.api

import com.langlez.echo.domain.PostMedia
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

class EchoRequest {
    data class CreatePost(
        @field:NotBlank
        @field:Size(max = 1000)
        val content: String,

        @field:Valid
        val media: List<MediaItem> = emptyList()
    )

    data class MediaItem(
        @field:NotBlank
        val url: String,
        val type: PostMedia.Type
    )

    data class ReportPost(
        @field:NotBlank
        @field:Size(max = 500)
        val reason: String
    )
}
