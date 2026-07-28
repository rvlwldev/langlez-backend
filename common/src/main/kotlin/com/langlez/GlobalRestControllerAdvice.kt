package com.langlez

import com.langlez.exception.LanglezException
import com.langlez.exception.ExceptionResponse
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.NoSuchMessageException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException
import java.util.Locale

@RestControllerAdvice
class GlobalRestControllerAdvice(private val source: MessageSource) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 사용자 정의 예외 */
    @ExceptionHandler(LanglezException::class)
    fun handleLanglezException(e: LanglezException, locale: Locale): ResponseEntity<ExceptionResponse> =
        ResponseEntity.status(e.status)
            .body(ExceptionResponse(e.status, resolveMessage(e.message ?: "error.unexpected", locale)))

    /** 인증 실패 */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException, locale: Locale): ResponseEntity<ExceptionResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ExceptionResponse(HttpStatus.UNAUTHORIZED, resolveMessage(e.message ?: "auth.unauthorized", locale)))

    /** 권한 없음 */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAuthenticationException(e: AccessDeniedException, locale: Locale): ResponseEntity<ExceptionResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ExceptionResponse(HttpStatus.FORBIDDEN, resolveMessage(e.message ?: "auth.forbidden", locale)))

    /** 잘못된 Body 요청, 잘못된 DTO 타입 등 */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<ExceptionResponse> =
        ResponseEntity.badRequest()
            .body(ExceptionResponse(HttpStatus.BAD_REQUEST, "common.bad-request"))

    /** Body 요청의 필수값 누락 등 */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ExceptionResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage ?: "invalid"}" }
            .ifBlank { "common.invalid-argument" }

        return ResponseEntity.badRequest()
            .body(ExceptionResponse(HttpStatus.BAD_REQUEST, message))
    }

    /** 올바르지 않은 엔드포인트 요청 */
    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNotFoundException(e: NoHandlerFoundException): ResponseEntity<ExceptionResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ExceptionResponse(HttpStatus.NOT_FOUND, "common.not-found-endpoint"))

    /** 처리되지 않은 예외 */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception, locale: Locale): ResponseEntity<ExceptionResponse> {
        logger.error("Unhandled Exception: {}", e.message, e)

        val message = resolveMessage("error.unexpected", locale)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ExceptionResponse(HttpStatus.INTERNAL_SERVER_ERROR, message))
    }

    private fun resolveMessage(key: String, locale: Locale): String =
        try {
            source.getMessage(key, null, locale)
        } catch (_: NoSuchMessageException) {
            runCatching { source.getMessage(key, null, Locale.ENGLISH) }.getOrDefault(key)
        }
}
