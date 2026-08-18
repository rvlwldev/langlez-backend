package com.langlez.exception

import org.springframework.http.HttpStatus

open class LanglezException(
    val status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    constructor(status: Int, message: String?, cause: Throwable?) : this(
        status = resolveStatus(status),
        message = message,
        cause = cause
    )

    constructor(status: Int, message: String?) : this(
        status = resolveStatus(status),
        message = message
    )

    constructor(e: Exception) : this(
        status = HttpStatus.INTERNAL_SERVER_ERROR,
        message = e.message,
        cause = e
    )

    constructor(e: Exception, message: String?) : this(
        status = HttpStatus.INTERNAL_SERVER_ERROR,
        message = message,
        cause = e
    )

    companion object {
        private fun resolveStatus(status: Int) = runCatching { HttpStatus.valueOf(status) }
            .getOrElse { HttpStatus.INTERNAL_SERVER_ERROR }
    }
}
