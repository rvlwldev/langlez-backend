package com.langlez.matching.application

import com.langlez.matching.domain.MatchingQueueRepository
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 매칭 요청이 없어도 이미 대기 중인 두 사람이 서로 매칭될 수 있도록 5초마다 큐 전체를 순회하며 재매칭을 시도한다.
 */
@Component
class MatchingScheduler(
    private val queueRepository: MatchingQueueRepository,
    private val matchingService: MatchingService,
) {

    @Scheduled(fixedDelay = 5000)
    @DistributedLock(prefix = "lock:matching-scheduler", ttl = 10, wait = 0, retries = 0, throwOnFailure = false)
    fun rematchWaitingMembers() {
        queueRepository.allMembers().forEach { memberId ->
            matchingService.attemptMatch(memberId)
        }
    }
}
