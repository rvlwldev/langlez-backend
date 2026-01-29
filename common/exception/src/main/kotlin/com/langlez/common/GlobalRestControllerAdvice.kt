package com.langlez.common

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalRestControllerAdvice {
    private val log = LoggerFactory.getLogger(this::class.java)

    @ExceptionHandler(CommonException::class)
    fun handleCommonException(e: CommonException): ResponseEntity<CommonErrorResponse> {
        val errorCode = e.errorCode

        if (errorCode.status.is5xxServerError) {
            log.error("CommonException: code={}, message={}", errorCode.code, errorCode.message, e)
        } else {
            log.warn("CommonException: code={}, message={}", errorCode.code, errorCode.message)
        }

        return ResponseEntity
            .status(errorCode.status)
            .body(CommonErrorResponse(errorCode.code, errorCode.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<CommonErrorResponse> {
        log.error("Unhandled Exception: {}", e.message, e)
        val errorCode = GlobalCommonError.INTERNAL_SERVER_ERROR
        return ResponseEntity
            .status(errorCode.status)
            .body(CommonErrorResponse(errorCode.code, errorCode.message))
    }
}
