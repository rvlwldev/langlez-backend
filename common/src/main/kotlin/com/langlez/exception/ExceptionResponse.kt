package com.langlez.exception

import com.langlez.core.LanglezException
import org.springframework.http.HttpStatus

class ExceptionResponse(val status: Int, val message: String?) {
    constructor(status: HttpStatus, message: String?) : this(status.value(), message)
    constructor(e: LanglezException) : this(e.status, e.message)
}
