package com.langlez.web.i18n

import org.springframework.context.support.ResourceBundleMessageSource
import java.text.MessageFormat
import java.util.Locale

class FallbackMessageSource(basenames: List<String>) : ResourceBundleMessageSource() {

    init {
        setBasenames(*basenames.toTypedArray())
        setDefaultEncoding("UTF-8")
        setDefaultLocale(AcceptLanguageLocaleResolver.DEFAULT)
    }

    override fun resolveCodeWithoutArguments(code: String, locale: Locale): String? =
        super.resolveCodeWithoutArguments(code, locale)
            ?: super.resolveCodeWithoutArguments(code, AcceptLanguageLocaleResolver.DEFAULT)

    override fun resolveCode(code: String, locale: Locale): MessageFormat? =
        super.resolveCode(code, locale)
            ?: super.resolveCode(code, AcceptLanguageLocaleResolver.DEFAULT)
}
