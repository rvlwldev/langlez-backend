package com.langlez.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.LocaleResolver
import java.util.Locale

class AcceptLanguageLocaleResolver : LocaleResolver {

    override fun resolveLocale(request: HttpServletRequest): Locale {
        val header = request.getHeader("Accept-Language") ?: return DEFAULT
        val ranges = try {
            Locale.LanguageRange.parse(header)
        } catch (_: IllegalArgumentException) {
            return DEFAULT
        }
        return ranges.asSequence()
            .filter { it.weight > 0 }
            .mapNotNull { supported(Locale.forLanguageTag(it.range)) }
            .firstOrNull() ?: DEFAULT
    }

    override fun setLocale(request: HttpServletRequest, response: HttpServletResponse?, locale: Locale?) {
        throw UnsupportedOperationException("Accept-Language 헤더로만 언어를 정한다")
    }

    private fun supported(locale: Locale): Locale? =
        when {
            locale.language == "zh" -> if (locale.script == "Hant" || locale.country in TRADITIONAL_REGIONS) TRADITIONAL_CHINESE else SIMPLIFIED_CHINESE
            locale.language in LANGUAGES -> Locale.of(locale.language)
            else -> null
        }

    companion object {
        val DEFAULT: Locale = Locale.ENGLISH
        private val SIMPLIFIED_CHINESE = Locale.of("zh", "CN")
        private val TRADITIONAL_CHINESE = Locale.of("zh", "TW")
        private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")
        private val LANGUAGES = setOf("ko", "en", "ja", "es", "fr", "de", "pt", "ru", "vi", "id")
    }
}
