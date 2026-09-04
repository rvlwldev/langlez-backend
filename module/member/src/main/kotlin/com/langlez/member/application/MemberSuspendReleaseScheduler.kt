package com.langlez.member.application

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.MemberSuspendHistory
import com.langlez.member.domain.MemberSuspendHistoryRepository
import com.langlez.redis.distributedLock.DistributedLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * 기간 정지 만료 해제.
 *
 * `MemberSuspendHistory.releaseAt` 은 처음부터 기록되고 있었지만 **그 값을 읽는 코드가 없었다.**
 * "7일 정지"가 실제로는 영구 정지였다. `IDX_MEMBER_SUSPEND_RELEASED` 인덱스까지 만들어 두고
 * 배치만 없던 상태다.
 *
 * [MemberSuspender] 와 별도 빈이다. `@DistributedLock` 은 Spring AOP 프록시로 도는데
 * 같은 클래스 안에서 부르면 advice 를 타지 않아 락이 조용히 안 걸린다.
 */
@Component
internal class MemberSuspendReleaseScheduler(
    private val repo: MemberRepository,
    private val suspendRepo: MemberSuspendHistoryRepository,
    private val tx: TransactionTemplate,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 */10 * * * *")
    @DistributedLock(prefix = "lock:member-suspend-release")
    fun releaseExpired() = releaseExpiredBefore(Instant.now())

    /**
     * 기준 시각을 인자로 받는다. `@Scheduled` 는 인자 있는 메서드에 못 붙어 진입점을 따로 둔다.
     * 테스트가 경계 시각을 고정할 수 있어야 "아직 안 지난 정지는 안 푼다"를 검증할 수 있다.
     *
     * **한 주기에 [CHUNK] 건까지만 처리한다.** 남으면 10분 뒤가 마저 가져간다. 다 비울 때까지
     * 도는 루프를 두면, 아래처럼 실패를 삼키는 구조에서 같은 행이 계속 걸려 무한 루프가 된다.
     */
    fun releaseExpiredBefore(now: Instant) {
        suspendRepo.findExpired(now, CHUNK)
            .groupBy(MemberSuspendHistory::memberId)
            .forEach { (memberId, histories) -> release(memberId, histories, now) }
    }

    /**
     * 회원 하나씩 따로 커밋한다. 한 트랜잭션에 묶으면 회원 하나에서 난 `@Version` 경합이
     * 그 주기에 처리한 나머지를 전부 되돌린다. 같은 이유로 실패는 로그만 남기고 넘어간다 —
     * 다음 주기가 다시 잡는다.
     */
    private fun release(memberId: Long, histories: List<MemberSuspendHistory>, now: Instant) {
        runCatching {
            tx.execute {
                // 만료된 이력만 보고 회원을 풀면 안 된다. 정지 중에 더 긴 정지가 덧붙은 회원은
                // 짧은 쪽이 만료되는 순간 남은 기간을 통째로 잃는다 (3일 정지 → 1일차에 30일 추가
                // → 3일차에 ACTIVE). 아직 유효한 열린 이력이 남아 있으면 상태는 그대로 두고
                // 만료된 이력만 닫는다. 무기한 정지(releaseAt == null)도 "아직 유효"에 든다.
                if (!hasLivingSuspension(memberId, now)) {
                    repo.find(memberId)
                        ?.takeIf { it.status == Member.Status.SUSPENDED }
                        ?.apply { unsuspend() }
                        ?.let(repo::save)
                }

                // 상태를 못 되돌린 경우에도 이력은 닫는다. 탈퇴한 회원과 위의 다중 정지가 그 경우다 —
                // Member.unsuspend() 가 require(status != WITHDRAWN) 로 던지므로 상태는 건드리지 않는다.
                // 안 닫으면 그 행이 매 주기 다시 걸려 배치가 영원히 같은 일을 한다.
                suspendRepo.saveAll(histories.onEach(MemberSuspendHistory::release))
            }
        }.onFailure { logger.warn("정지 만료 해제 실패. member={}", memberId, it) }
    }

    /** 이번에 닫을 이력을 빼고도 [now] 기준으로 아직 살아 있는 정지가 남아 있는가. */
    private fun hasLivingSuspension(memberId: Long, now: Instant): Boolean =
        suspendRepo.findOpen(memberId).any { it.releaseAt == null || it.releaseAt > now }

    private companion object {
        const val CHUNK = 500
    }
}
