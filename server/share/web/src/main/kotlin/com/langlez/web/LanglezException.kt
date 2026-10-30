package com.langlez.web

import org.springframework.http.HttpStatus

class LanglezException(
    val status: HttpStatus,
    val key: String,
    vararg val args: Any,
    cause: Throwable? = null,
) : RuntimeException(key, cause)
