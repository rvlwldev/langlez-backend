package com.langlez.matching.domain

import com.langlez.profile.domain.Profile
import java.time.Instant

data class QueueMemberMeta(
    val joinedAt: Instant,
    val filter: MatchingQueueFilter? = null,
)

data class MatchingQueueFilter(
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val languageLevel: Profile.LanguageLevel? = null,
) {
    init {
        require(minAge == null || minAge >= 0) { "minAge must be non-negative" }
        require(maxAge == null || maxAge >= 0) { "maxAge must be non-negative" }
        require(minAge == null || maxAge == null || minAge <= maxAge) { "minAge must be less than or equal to maxAge" }
    }

    fun isPresent(): Boolean = minAge != null || maxAge != null || languageLevel != null
}

