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
    fun handleLanglezException(e: LanglezException, locale: Locale): ResponseEntity<ExceptionResponse> {
        // 5xx 는 우리 잘못이라 스택까지 남긴다. 4xx 는 클라이언트 입력 문제라 한 줄이면 충분하다.
        if (e.status.is5xxServerError) logger.error("Langlez 5xx: {}", e.message, e)
        else logger.debug("Langlez {}: {}", e.status.value(), e.message)

        return ResponseEntity.status(e.status)
            .body(ExceptionResponse(e.status, resolveMessage(e.message ?: "error.unexpected", locale)))
    }

    /** 인증 실패 */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException, locale: Locale): ResponseEntity<ExceptionResponse> {
        logger.warn("Authentication failed: {}", e.message)
        // e.message 를 키로 쓰면 "Full authentication is required..." 같은 Spring 내부 문자열이
        // 키로 조회 실패해 그대로 클라이언트에 노출된다. 고정 키만 쓴다.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ExceptionResponse(HttpStatus.UNAUTHORIZED, resolveMessage("auth.unauthorized", locale)))
    }

    /** 권한 없음 */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(e: AccessDeniedException, locale: Locale): ResponseEntity<ExceptionResponse> {
        logger.warn("Access denied: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ExceptionResponse(HttpStatus.FORBIDDEN, resolveMessage("auth.forbidden", locale)))
    }

    /** 잘못된 Body 요청, 잘못된 DTO 타입 등 */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadableException(
        e: HttpMessageNotReadableException,
        locale: Locale,
    ): ResponseEntity<ExceptionResponse> =
        ResponseEntity.badRequest()
            .body(ExceptionResponse(HttpStatus.BAD_REQUEST, resolveMessage("common.bad-request", locale)))

    /** Body 요청의 필수값 누락 등 */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        e: MethodArgumentNotValidException,
        locale: Locale,
    ): ResponseEntity<ExceptionResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${resolveMessage(it.defaultMessage ?: "invalid", locale)}" }
            .ifBlank { "common.invalid-argument" }

        return ResponseEntity.badRequest()
            .body(ExceptionResponse(HttpStatus.BAD_REQUEST, message))
    }

    /** 올바르지 않은 엔드포인트 요청 */
    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNotFoundException(e: NoHandlerFoundException, locale: Locale): ResponseEntity<ExceptionResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ExceptionResponse(HttpStatus.NOT_FOUND, resolveMessage("common.not-found-endpoint", locale)))

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
