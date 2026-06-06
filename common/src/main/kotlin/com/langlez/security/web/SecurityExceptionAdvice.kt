package com.langlez.security.web

import com.langlez.exception.ExceptionResponse
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.Locale

@RestControllerAdvice
class SecurityExceptionAdvice(private val source: MessageSource) {

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException, locale: Locale): ResponseEntity<ExceptionResponse> {
        val message = getMessage("auth.unauthorized", locale)
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ExceptionResponse(HttpStatus.UNAUTHORIZED, message))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(e: AccessDeniedException, locale: Locale): ResponseEntity<ExceptionResponse> {
        val message = getMessage("auth.forbidden", locale)
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ExceptionResponse(HttpStatus.FORBIDDEN, message))
    }

    private fun getMessage(key: String, locale: Locale): String =
        try { source.getMessage(key, null, locale) }
        catch (_: Exception) { key }
}
