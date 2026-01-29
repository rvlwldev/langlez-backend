package com.langlez.swagger.utils

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

@ParameterObject
data class SwaggerPageable(
    @field:Parameter(description = "Page number (0-based)", example = "0")
    @field:Schema(defaultValue = "0", minimum = "0")
    val page: Int = 0,
    @field:Parameter(description = "Page size", example = "20")
    @field:Schema(defaultValue = "20", minimum = "1", maximum = "100")
    val size: Int = 20,
    @field:Parameter(description = "Sort field", example = "createdAt")
    val sort: String? = null,
    @field:Parameter(
        description = "Sort direction",
        example = "desc",
        schema = Schema(allowableValues = ["asc", "desc"]),
    )
    val direction: String = "desc",
) {
    fun toPageable(): Pageable =
        if (sort != null) {
            val sortDirection = if (direction.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC
            PageRequest.of(page, size, Sort.by(sortDirection, sort))
        } else {
            PageRequest.of(page, size)
        }
}
