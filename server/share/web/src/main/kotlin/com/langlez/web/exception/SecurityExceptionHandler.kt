package com.langlez.web.exception

import com.langlez.web.i18n.I18N
import com.langlez.web.i18n.translate
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.MessageSource
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.Locale

@RestControllerAdvice
@ConditionalOnClass(name = ["org.springframework.security.core.AuthenticationException"])
@Order(Ordered.LOWEST_PRECEDENCE - 1)
class SecurityExceptionHandler(private val messages: MessageSource) {

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(e: AuthenticationException, locale: Locale): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.UNAUTHORIZED, I18N.COMMON.UNAUTHORIZED, locale)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException, locale: Locale): ResponseEntity<ExceptionResponse> =
        respond(HttpStatus.FORBIDDEN, I18N.COMMON.FORBIDDEN, locale)

    private fun respond(status: HttpStatus, key: String, locale: Locale): ResponseEntity<ExceptionResponse> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(ExceptionResponse(messages.translate(key, locale)))
}
