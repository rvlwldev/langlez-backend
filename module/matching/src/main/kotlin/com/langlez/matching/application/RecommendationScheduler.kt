package com.langlez.matching.application

import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 무료 회원: 매일 자정(00:00) 추천 목록 15명 갱신 / 유료 회원: 매시간 갱신.
 */
@Component
class RecommendationScheduler(
    private val recommendationService: RecommendationService,
) {

    @Scheduled(cron = "0 0 0 * * *")
    @DistributedLock(prefix = "lock:matching-recommend-free", ttl = 300, wait = 0, retries = 0, throwOnFailure = false)
    fun refreshFreeMembers() {
        recommendationService.refreshFreeMembers()
    }

    @Scheduled(fixedRate = 3600000)
    @DistributedLock(prefix = "lock:matching-recommend-premium", ttl = 300, wait = 0, retries = 0, throwOnFailure = false)
    fun refreshPremiumMembers() {
        recommendationService.refreshPremiumMembers()
    }
}
