package com.langlez

import com.langlez.exception.ExceptionResponse
import com.langlez.core.LanglezException
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException
import java.util.Locale

@RestControllerAdvice
class GlobalRestControllerAdvice(private val source: MessageSource) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @ExceptionHandler(LanglezException::class)
    fun handleLanglezException(e: LanglezException, locale: Locale): ResponseEntity<ExceptionResponse> {
        val messageKey = e.message ?: "error.unexpected"
        val message = getMessage(messageKey, locale)
        return ResponseEntity
            .status(e.status)
            .body(ExceptionResponse(e.status, message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        e: MethodArgumentNotValidException,
        locale: Locale
    ): ResponseEntity<ExceptionResponse> {
        val message = getMessage("common.invalid-argument", locale)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ExceptionResponse(HttpStatus.BAD_REQUEST, message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadableException(
        e: HttpMessageNotReadableException,
        locale: Locale
    ): ResponseEntity<ExceptionResponse> {
        val message = getMessage("common.bad-request", locale)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ExceptionResponse(HttpStatus.BAD_REQUEST, message))
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNotFoundException(e: NoHandlerFoundException, locale: Locale): ResponseEntity<ExceptionResponse> {
        val message = getMessage("common.not-found", locale)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ExceptionResponse(HttpStatus.NOT_FOUND, message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception, locale: Locale): ResponseEntity<ExceptionResponse> {
        logger.error("Unhandled Exception: {}", e.message, e)

        val message = getMessage("error.unexpected", locale)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ExceptionResponse(HttpStatus.INTERNAL_SERVER_ERROR, message))
    }

    private fun getMessage(key: String, locale: Locale): String {
        return try {
            source.getMessage(key, null, locale)
        } catch (_: Exception) {
            try {
                source.getMessage(key, null, Locale.ENGLISH)
            } catch (_: Exception) {
                key
            }
        }
    }
}