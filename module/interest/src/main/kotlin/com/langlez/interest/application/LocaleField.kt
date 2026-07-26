package com.langlez.interest.application

import java.util.Locale

/** Accept-Language 등에서 얻은 Locale을 Interest 엔티티의 언어 필드명으로 매핑한다. */
object LocaleField {
    private val TAG_TO_FIELD = mapOf(
        "ko" to "ko", "en" to "en", "ja" to "ja",
        "zh-TW" to "zhTW", "zh-CN" to "zhCN", "de" to "de",
        "vi" to "vi", "id" to "ind", "fr" to "fr",
        "pt" to "pt", "es" to "es", "ru" to "ru",
    )

    fun of(locale: Locale): String = TAG_TO_FIELD[locale.toLanguageTag()] ?: "en"
}
