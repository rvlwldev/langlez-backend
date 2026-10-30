package com.langlez.web

import jakarta.validation.ConstraintViolationException
import org.springframework.context.MessageSource
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.Locale

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 1)
class ValidationExceptionHandler(private val messages: MessageSource) {

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(e: ConstraintViolationException, locale: Locale): ResponseEntity<ExceptionResponse> {
        val errors = e.constraintViolations.orEmpty().map { it.propertyPath.last().name to it.message }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
            .body(ExceptionResponse(messages.translateInvalid(errors, locale)))
    }
}
