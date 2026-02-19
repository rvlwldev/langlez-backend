package com.langlez.common.exception

import org.springframework.http.HttpStatus

open class DataConflictException(
    message: String? = null,
    cause: Throwable? = null
) : LanglezException(HttpStatus.CONFLICT, message, cause)

open class ConcurrentUpdateException(
    message: String? = null,
    cause: Throwable? = null
) : LanglezException(HttpStatus.CONFLICT, message, cause)
