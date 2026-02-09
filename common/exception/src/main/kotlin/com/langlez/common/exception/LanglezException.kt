package com.langlez.common.exception

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR

open class LanglezException(
    val status: HttpStatus = INTERNAL_SERVER_ERROR,
    message: String? = null,
    cause: Throwable? = null
) : RuntimeException()