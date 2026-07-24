package com.langlez.matching.domain

import com.langlez.profile.domain.Profile

data class MatchingQueueFilter(
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val languageLevel: Profile.LanguageLevel? = null,
) {
    fun isPresent(): Boolean = minAge != null || maxAge != null || languageLevel != null
}
