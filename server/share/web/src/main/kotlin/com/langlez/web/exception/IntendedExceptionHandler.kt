package com.langlez.web.exception

import com.langlez.web.i18n.statusKey
import com.langlez.web.i18n.translate
import com.langlez.web.i18n.translateInvalid
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.server.ResponseStatusException
import java.util.Locale

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 2)
class IntendedExceptionHandler(private val messages: MessageSource) {

    private val logger = KotlinLogging.logger {}

    @ExceptionHandler(LanglezException::class)
    fun handleLanglez(e: LanglezException, locale: Locale): ResponseEntity<ExceptionResponse> {
        if (e.status.is5xxServerError) logger.error(e) { "Langlez ${e.status.value()}: ${e.key}" }
        return respond(e.status, messages.getMessage(e.key, e.args, e.key, locale)!!)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(e: ResponseStatusException, locale: Locale): ResponseEntity<ExceptionResponse> {
        if (e.statusCode.is5xxServerError) logger.error(e) { "ResponseStatusException ${e.statusCode.value()}: ${e.reason}" }
        val message = messages.translate(e.reason.orEmpty().ifBlank { statusKey(e.statusCode.value()) }, locale)
        return respond(e.statusCode, message, e.headers)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(e: HandlerMethodValidationException, locale: Locale): ResponseEntity<ExceptionResponse> {
        val errors = e.parameterValidationResults.flatMap { result ->
            result.resolvableErrors.map { error ->
                ((error as? FieldError)?.field ?: result.methodParameter.parameterName) to error.defaultMessage
            }
        }
        return respond(HttpStatus.BAD_REQUEST, messages.translateInvalid(errors, locale))
    }

    private fun respond(status: HttpStatusCode, message: String, headers: HttpHeaders = HttpHeaders.EMPTY): ResponseEntity<ExceptionResponse> =
        ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_JSON).body(ExceptionResponse(message))
}
