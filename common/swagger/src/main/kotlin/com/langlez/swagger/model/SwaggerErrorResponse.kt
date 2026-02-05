package com.langlez.swagger.model

import com.langlez.common.CommonErrorResponse
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Standard API error response")
data class SwaggerErrorResponse(
    @field:Schema(description = "Error code", example = "USER_NOT_FOUND")
    val code: String,
    @field:Schema(description = "Error message", example = "User not found")
    val message: String,
    @field:Schema(description = "Additional error data")
    val data: Any? = null,
) {
    fun toCommon(): CommonErrorResponse = CommonErrorResponse(code, message, data)
}
