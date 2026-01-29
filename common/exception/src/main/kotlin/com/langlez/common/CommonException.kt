package com.langlez.common

import org.springframework.http.HttpStatus

open class CommonException(
    val errorCode: CommonError,
    override val cause: Throwable? = null,
) : RuntimeException(errorCode.message, cause) {
    val status: HttpStatus
        get() = errorCode.status

    val code: String
        get() = errorCode.code
}
