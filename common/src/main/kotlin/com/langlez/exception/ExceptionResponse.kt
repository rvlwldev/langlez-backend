package com.langlez.exception

import org.springframework.http.HttpStatus

class ExceptionResponse(val status: HttpStatus, val message: String) {
    constructor(status: Int, message: String?) : this(resolveStatus(status), message ?: "error.unexpected")
    constructor(e: LanglezException) : this(e.status, e.message ?: "error.unexpected")

    companion object {
        private fun resolveStatus(status: Int) = runCatching { HttpStatus.valueOf(status) }
            .getOrElse { HttpStatus.INTERNAL_SERVER_ERROR }
    }
}
