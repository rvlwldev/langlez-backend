package com.langlez.web.exception

import com.langlez.web.i18n.I18N
import com.langlez.web.i18n.statusKey
import com.langlez.web.i18n.translate
import com.langlez.web.i18n.translateInvalid
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.TypeMismatchException
import org.springframework.context.MessageSource
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MultipartException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.util.Locale

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class GlobalExceptionHandler(private val messages: MessageSource) {

    private val logger = KotlinLogging.logger {}

    @ExceptionHandler(HttpMessageNotReadableException::class, TypeMismatchException::class)
    fun handleBadRequest(e: Exception, locale: Locale): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.BAD_REQUEST, messages.translate(I18N.COMMON.BAD_REQUEST, locale))

    @ExceptionHandler(BindException::class)
    fun handleBind(e: BindException, locale: Locale): ResponseEntity<ExceptionResponse> =
        if (e.allErrors.any { it.codes.isNullOrEmpty() || (it as? FieldError)?.isBindingFailure == true }) handleBadRequest(e, locale)
        else respondInvalid(e.allErrors.map { (it as? FieldError)?.field to it.defaultMessage }, locale)

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun handleNotFound(e: Exception, locale: Locale): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.NOT_FOUND, messages.translate(I18N.COMMON.NOT_FOUND_ENDPOINT, locale))

    @ExceptionHandler(MultipartException::class)
    fun handleMultipart(e: MultipartException, locale: Locale): ResponseEntity<ExceptionResponse> =
        if (e is ErrorResponse) handleOthers(e, locale)
        else respond(HttpStatus.BAD_REQUEST, messages.translate(I18N.COMMON.BAD_REQUEST, locale))

    @ExceptionHandler(Exception::class)
    fun handleOthers(e: Exception, locale: Locale): ResponseEntity<ExceptionResponse> {
        val status = if (e is ErrorResponse) e.statusCode else HttpStatus.INTERNAL_SERVER_ERROR
        if (status.is5xxServerError) logger.error(e) { "처리되지 않은 예외" }
        val headers = if (e is ErrorResponse) e.headers else HttpHeaders.EMPTY
        return respond(status, messages.translate(statusKey(status.value()), locale), headers)
    }

    private fun respondInvalid(errors: List<Pair<String?, String?>>, locale: Locale): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.BAD_REQUEST, messages.translateInvalid(errors, locale))

    private fun respond(status: HttpStatusCode, message: String, headers: HttpHeaders = HttpHeaders.EMPTY): ResponseEntity<ExceptionResponse> =
        ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_JSON).body(ExceptionResponse(message))
}
