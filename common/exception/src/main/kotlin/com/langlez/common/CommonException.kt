package com.langlez.common

import org.springframework.http.HttpStatus

open class CommonException(
    val error: CommonError,
    message: String?,
    cause: Throwable?,
) : RuntimeException(message ?: error.message, cause) {
    val status: HttpStatus get() = error.status
    val code: String get() = error.code
}