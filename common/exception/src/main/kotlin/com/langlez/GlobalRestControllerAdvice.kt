package com.langlez

import com.langlez.common.exception.ExceptionResponse
import com.langlez.common.exception.LanglezException
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.*

@RestControllerAdvice
class GlobalRestControllerAdvice(private val source: MessageSource) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @ExceptionHandler(LanglezException::class)
    fun handleLanglezException(e: LanglezException, locale: Locale) =
        ExceptionResponse(e.status, source.getMessage(e.message, null, locale))

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception, locale: Locale): ExceptionResponse {
        logger.error("Unhandled Exception: {}", e.message, e)
        return ExceptionResponse(500, source.getMessage("error.unexpected", null, locale))
    }

}
