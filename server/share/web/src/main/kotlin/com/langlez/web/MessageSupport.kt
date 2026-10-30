package com.langlez.web

import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import java.util.Locale

fun MessageSource.translate(key: String, locale: Locale): String = getMessage(key, null, key, locale)!!

fun statusKey(status: Int): String =
    when (status) {
        HttpStatus.UNAUTHORIZED.value() -> I18N.COMMON.UNAUTHORIZED
        HttpStatus.FORBIDDEN.value() -> I18N.COMMON.FORBIDDEN
        HttpStatus.NOT_FOUND.value() -> I18N.COMMON.NOT_FOUND
        HttpStatus.METHOD_NOT_ALLOWED.value() -> I18N.COMMON.METHOD_NOT_ALLOWED
        HttpStatus.CONFLICT.value() -> I18N.COMMON.CONFLICT
        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value() -> I18N.COMMON.UNSUPPORTED_MEDIA_TYPE
        in 400..499 -> I18N.COMMON.BAD_REQUEST
        else -> I18N.COMMON.UNEXPECTED
    }
