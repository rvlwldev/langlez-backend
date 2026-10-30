package com.langlez.web

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.beans.TypeMismatchException
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.util.Locale

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class GlobalExceptionHandler(private val messages: MessageSource) {

    private val logger = KotlinLogging.logger {}

    @ExceptionHandler(LanglezException::class)
    fun handleLanglez(e: LanglezException, locale: Locale): ResponseEntity<ExceptionResponse> {
        if (e.status.is5xxServerError) logger.error(e) { "Langlez ${e.status.value()}: ${e.key}" }
        return respond(e.status, messages.getMessage(e.key, e.args, e.key, locale)!!)
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        ServletRequestBindingException::class,
        TypeMismatchException::class,
    )
    fun handleBadRequest(e: Exception, locale: Locale): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.BAD_REQUEST, translate(I18N.COMMON.BAD_REQUEST, locale))

    @ExceptionHandler(BindException::class)
    fun handleBind(e: BindException, locale: Locale): ResponseEntity<ExceptionResponse> =
        respondInvalid(e.allErrors.map { (it as? FieldError)?.field to it.defaultMessage }, locale)

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(e: HandlerMethodValidationException, locale: Locale): ResponseEntity<ExceptionResponse> =
        respondInvalid(
            e.parameterValidationResults.flatMap { result ->
                result.resolvableErrors.map { error ->
                    ((error as? FieldError)?.field ?: result.methodParameter.parameterName) to error.defaultMessage
                }
            },
            locale,
        )

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun handleNotFound(e: Exception, locale: Locale): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.NOT_FOUND, translate(I18N.COMMON.NOT_FOUND_ENDPOINT, locale))

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(e: HttpRequestMethodNotSupportedException, locale: Locale): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.METHOD_NOT_ALLOWED, translate(I18N.COMMON.METHOD_NOT_ALLOWED, locale), e.headers)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(e: HttpMediaTypeNotSupportedException, locale: Locale): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, translate(I18N.COMMON.UNSUPPORTED_MEDIA_TYPE, locale), e.headers)

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(e: ResponseStatusException, locale: Locale): ResponseEntity<ExceptionResponse> {
        if (e.statusCode.is5xxServerError) logger.error(e) { "ResponseStatusException ${e.statusCode.value()}: ${e.reason}" }
        val reason = e.reason
        val message = if (reason == null) translate(I18N.COMMON.UNEXPECTED, locale) else messages.getMessage(reason, null, reason, locale)!!
        return respond(e.statusCode, message, e.headers)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception, locale: Locale): ResponseEntity<ExceptionResponse> {
        logger.error(e) { "처리되지 않은 예외" }
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, translate(I18N.COMMON.UNEXPECTED, locale))
    }

    private fun respondInvalid(errors: List<Pair<String?, String?>>, locale: Locale): ResponseEntity<ExceptionResponse> {
        val message = errors
            .joinToString(", ") { (field, text) ->
                val translated = text?.let { messages.getMessage(it, null, it, locale)!! } ?: translate(I18N.COMMON.INVALID_ARGUMENT, locale)
                if (field == null) translated else "$field: $translated"
            }
            .ifBlank { translate(I18N.COMMON.INVALID_ARGUMENT, locale) }
        return respond(HttpStatus.BAD_REQUEST, message)
    }

    private fun translate(key: String, locale: Locale): String = messages.getMessage(key, null, key, locale)!!

    private fun respond(status: HttpStatusCode, message: String, headers: HttpHeaders = HttpHeaders.EMPTY): ResponseEntity<ExceptionResponse> =
        ResponseEntity.status(status).headers(headers).body(ExceptionResponse(message))
}
