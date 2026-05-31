package com.langlez.profile.application

import com.langlez.profile.domain.ProfileRepository
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class VisitCountSyncScheduler(private val repo: ProfileRepository) {

    @Scheduled(fixedDelay = 60_000)
    @DistributedLock(prefix = "lock:visit-count-sync", ttl = 30, wait = 0, retries = 0, throwOnFailure = false)
    fun syncVisitCounts() {
        val counts = repo.flushVisitCounts()
        if (counts.isEmpty()) return

        counts.forEach { (username, delta) ->
            repo.incrementVisitCount(username, delta)
        }
    }
}
