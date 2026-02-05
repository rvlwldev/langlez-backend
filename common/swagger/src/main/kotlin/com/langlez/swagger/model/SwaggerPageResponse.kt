package com.langlez.swagger.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Paginated response wrapper")
data class SwaggerPageResponse<T>(
    @field:Schema(description = "List of items")
    val content: List<T>,
    @field:Schema(description = "Current page number (0-based)", example = "0")
    val page: Int,
    @field:Schema(description = "Number of items per page", example = "20")
    val size: Int,
    @field:Schema(description = "Total number of elements", example = "100")
    val totalElements: Long,
    @field:Schema(description = "Total number of pages", example = "5")
    val totalPages: Int,
    @field:Schema(description = "Whether this is first page", example = "true")
    val first: Boolean,
    @field:Schema(description = "Whether this is last page", example = "false")
    val last: Boolean,
    @field:Schema(description = "Number of elements in current page", example = "20")
    val numberOfElements: Int,
    @field:Schema(description = "Whether this page has content", example = "true")
    val hasContent: Boolean,
    @field:Schema(description = "Whether there is a next page", example = "true")
    val hasNext: Boolean,
    @field:Schema(description = "Whether there is a previous page", example = "false")
    val hasPrevious: Boolean,
) {
    companion object {
        fun <T> of(
            content: List<T>,
            page: Int,
            size: Int,
            totalElements: Long,
        ): SwaggerPageResponse<T> {
            val totalPages = ((totalElements + size - 1) / size).toInt()

            return SwaggerPageResponse(
                content = content,
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                first = page == 0,
                last = page >= totalPages - 1,
                numberOfElements = content.size,
                hasContent = content.isNotEmpty(),
                hasNext = page < totalPages - 1,
                hasPrevious = page > 0,
            )
        }
    }
}
