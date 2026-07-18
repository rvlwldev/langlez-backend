package com.langlez.matching.domain

import java.time.Duration

/** Redis에 캐싱되는 일별/시간별 추천 목록("matching:recommend:{memberId}"). */
interface RecommendationRepository {

    fun save(memberId: Long, usernames: List<String>, ttl: Duration)

    fun find(memberId: Long): List<String>?
}
