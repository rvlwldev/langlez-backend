package com.langlez.web

import org.springframework.boot.webmvc.error.DefaultErrorAttributes
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.WebRequest

class TranslatedErrorAttributes(private val messages: MessageSource) : DefaultErrorAttributes() {

    override fun getErrorAttributes(webRequest: WebRequest, options: ErrorAttributeOptions): Map<String, Any> {
        val status = super.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults())["status"] as Int
        val key = keyFor(status)
        return mapOf(
            "error" to true,
            "message" to messages.getMessage(key, null, key, LocaleContextHolder.getLocale())!!,
        )
    }

    private fun keyFor(status: Int): String =
        when (status) {
            HttpStatus.BAD_REQUEST.value() -> I18N.COMMON.BAD_REQUEST
            HttpStatus.UNAUTHORIZED.value() -> I18N.COMMON.UNAUTHORIZED
            HttpStatus.FORBIDDEN.value() -> I18N.COMMON.FORBIDDEN
            HttpStatus.NOT_FOUND.value() -> I18N.COMMON.NOT_FOUND_ENDPOINT
            HttpStatus.METHOD_NOT_ALLOWED.value() -> I18N.COMMON.METHOD_NOT_ALLOWED
            HttpStatus.CONFLICT.value() -> I18N.COMMON.CONFLICT
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value() -> I18N.COMMON.UNSUPPORTED_MEDIA_TYPE
            in 400..499 -> I18N.COMMON.BAD_REQUEST
            else -> I18N.COMMON.UNEXPECTED
        }
}
