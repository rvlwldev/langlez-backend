package com.langlez.common

data class CommonErrorResponse(
    val code: String,
    val message: String,
    val data: Any? = null,
)
