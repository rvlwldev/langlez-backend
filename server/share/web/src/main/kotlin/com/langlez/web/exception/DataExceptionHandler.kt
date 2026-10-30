package com.langlez.web.exception

import com.langlez.web.i18n.I18N
import com.langlez.web.i18n.translate
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.MessageSource
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.Locale

@RestControllerAdvice
@ConditionalOnClass(name = ["org.springframework.dao.DataAccessException"])
@Order(Ordered.LOWEST_PRECEDENCE - 1)
class DataExceptionHandler(private val messages: MessageSource) {

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(e: DataIntegrityViolationException, locale: Locale): ResponseEntity<ExceptionResponse> =
        respond(I18N.COMMON.CONFLICT, locale)

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(e: OptimisticLockingFailureException, locale: Locale): ResponseEntity<ExceptionResponse> =
        respond(I18N.COMMON.OPTIMISTIC_LOCK_FAILURE, locale)

    private fun respond(key: String, locale: Locale): ResponseEntity<ExceptionResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(ExceptionResponse(messages.translate(key, locale)))
}
