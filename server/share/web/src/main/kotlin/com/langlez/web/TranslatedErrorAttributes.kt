package com.langlez.web

import jakarta.servlet.RequestDispatcher
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.webmvc.error.DefaultErrorAttributes
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.WebRequest

class TranslatedErrorAttributes(private val messages: MessageSource) : DefaultErrorAttributes() {

    override fun getErrorAttributes(webRequest: WebRequest, options: ErrorAttributeOptions): Map<String, Any> {
        val status = webRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE, RequestAttributes.SCOPE_REQUEST) as? Int ?: 500
        return mapOf(
            "error" to true,
            "message" to messages.translate(statusKey(status), LocaleContextHolder.getLocale()),
        )
    }
}
