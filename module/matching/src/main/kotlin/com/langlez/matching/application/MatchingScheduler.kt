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
        // 대기열이 커져도 한 사이클이 무한정 길어지지 않도록 상한을 두고, 특정 대기자가 계속 밀리지 않도록 매 사이클 무작위로 섞는다.
        queueRepository.allMembers().shuffled().take(BATCH_SIZE).forEach { memberId ->
            matchingService.attemptMatch(memberId)
        }
    }

    companion object {
        private const val BATCH_SIZE = 200
    }
}
