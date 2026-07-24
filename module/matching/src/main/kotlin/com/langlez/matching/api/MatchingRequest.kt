package com.langlez.matching.api

import com.langlez.matching.domain.MatchingQueueFilter
import com.langlez.profile.domain.Profile

class MatchingRequest {

    /** 유료 전용 추천 목록 고급 필터. MEMBER role이 아래 값 중 하나라도 지정하면 403. */
    data class RecommendationFilter(
        val gender: Profile.Gender? = null,
        val minAge: Int? = null,
        val maxAge: Int? = null,
        val languageLevel: Profile.LanguageLevel? = null,
    ) {
        fun isPresent(): Boolean = gender != null || minAge != null || maxAge != null || languageLevel != null
    }

    /** 실시간 매칭 큐 전용 조건 필터 (성별 제외) */
    data class QueueFilter(
        val minAge: Int? = null,
        val maxAge: Int? = null,
        val languageLevel: Profile.LanguageLevel? = null,
    ) {
        fun isPresent(): Boolean = minAge != null || maxAge != null || languageLevel != null

        fun toDomain(): MatchingQueueFilter = MatchingQueueFilter(
            minAge = minAge,
            maxAge = maxAge,
            languageLevel = languageLevel,
        )
    }
}

