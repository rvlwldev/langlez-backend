package com.langlez.common.exception

import org.springframework.http.HttpStatus

class ExceptionResponse(val code: Int, val message: String?) {
    constructor(status: HttpStatus, message: String) : this(status.ordinal, message)
    constructor(e: LanglezException) : this(e.status.ordinal, e.message)
}