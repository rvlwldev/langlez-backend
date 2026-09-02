package com.langlez.profile.application

import com.langlez.member.contract.MemberQuery
import com.langlez.profile.domain.ProfileRepository
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class VisitCountSyncScheduler(
    private val repo: ProfileRepository,
    private val members: MemberQuery,
    private val transaction: TransactionTemplate,
) {

    @Scheduled(fixedDelay = 60_000)
    @DistributedLock(prefix = "lock:visit-count-sync", leaseSecs = 30, waitMs = 0, retries = 0, throwOnFailure = false)
    fun syncVisitCounts() {
        val counts = repo.beginVisitCountFlush()
        if (counts.isEmpty()) return

        // handle → 회원 id 변환은 member 포트다. 트랜잭션 안에서 부르면 원격이 됐을 때
        // 플러시 대상 수만큼 네트워크 왕복을 커넥션 쥔 채 기다린다. 먼저 다 끝낸다.
        // 이미 지워진 handle 은 여기서 빠진다 — 던지면 나머지 회원의 방문수까지 같이 롤백된다.
        val deltas = counts.mapNotNull { (username, delta) ->
            members.findIdByHandle(username)?.let { it to delta }
        }

        if (deltas.isNotEmpty()) transaction.execute {
            deltas.forEach { (memberId, delta) -> repo.incrementVisitCountInDb(memberId, delta) }
        }

        // 반영 못 한 handle 도 함께 정리한다. 남겨두면 dirty 셋에 영영 쌓인다.
        repo.commitVisitCountFlush(counts.keys)
    }
}
