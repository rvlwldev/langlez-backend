package com.langlez.member.application

import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberWriter
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.MemberSuspendHistory
import com.langlez.member.domain.MemberSuspendHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/**
 * 회원 정지·해제 운영 조치. `MemberWriter` 계약을 이 클래스가 직접 구현한다.
 *
 * [com.langlez.member.contract.MemberReader] 와 달리 infrastructure 에 어댑터를 두지 않는다.
 * 조회 포트는 `MemberRepository`(domain) 를 읽어 매핑하는 것뿐이라 어댑터가 domain 만 보지만,
 * 이건 상태 변경 + 이력 기록이 묶인 유스케이스라 어댑터를 두면 그 어댑터가 infrastructure 에서
 * application 을 참조하게 되어 의존 방향이 뒤집힌다. `ReportWriter` 를 `ReportService` 가
 * 직접 구현하는 것과 같은 이유다.
 *
 * `MemberService` 에서 갈라 나왔다 — 만료 배치([MemberSuspendReleaseScheduler])가
 * `@DistributedLock` 때문에 별도 빈이어야 하고, 정지 관련 상태 전이를 한곳에 모아야
 * "정지 → 이력 열림 → 해제 → 이력 닫힘" 이 한 파일에서 읽힌다.
 */
@Service
class MemberSuspender(
    private val repo: MemberRepository,
    private val suspendRepo: MemberSuspendHistoryRepository,
) : MemberWriter {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun suspend(memberId: Long, reason: String?, days: Long?, actorId: Long) {
        // 안전장치는 호출자가 아니라 여기 둔다. 회원의 역할을 아는 게 member 뿐이고,
        // 포트 쪽에 두면 새 소비자가 검사를 빠뜨렸을 때 열리는 게 아니라 닫혀야 한다.
        if (memberId == actorId) throw LanglezException(BAD_REQUEST, "member.suspend.self")

        val member = findOrThrow(memberId)

        // 운영자끼리 서로 잠그면 복구 수단이 DB 직접 수정밖에 남지 않는다.
        if (member.role == Member.Role.ADMIN) throw LanglezException(FORBIDDEN, "member.suspend.admin-target")

        try {
            member.suspend()
        } catch (e: IllegalArgumentException) {
            throw LanglezException(BAD_REQUEST, e.message, e)
        }

        repo.save(member)
        suspendRepo.save(MemberSuspendHistory(member, reason, days?.let(Duration::ofDays), actorId))

        logger.info("회원 정지. member={} actor={} days={}", memberId, actorId, days)
    }

    /** 어드민 정지 해제. 정지 이력도 함께 닫는다. */
    @Transactional
    override fun unsuspend(memberId: Long, actorId: Long) {
        val member = findOrThrow(memberId)

        try {
            member.unsuspend()
        } catch (e: IllegalArgumentException) {
            throw LanglezException(BAD_REQUEST, e.message, e)
        }

        repo.save(member)
        closeOpenHistories(memberId)

        // 해제한 사람을 남길 컬럼은 없다. 정지 이력 한 행에 actor 자리가 하나뿐이고 그건 정지시킨 쪽이다.
        // 해제자까지 추적해야 하면 별도 감사 테이블을 만들 일이지 이 행을 덮어쓸 일이 아니다.
        logger.info("회원 정지 해제. member={} actor={}", memberId, actorId)
    }

    /**
     * 열린 정지 이력을 닫는다.
     *
     * 안 닫으면 `isReleased` 가 영영 false 로 남아 [MemberSuspendReleaseScheduler] 가
     * 이미 풀린 회원을 매 주기 다시 잡는다. 정지 → 해제 → 재정지가 반복되면 열린 행이
     * 여럿일 수 있어 하나가 아니라 전부 닫는다.
     */
    private fun closeOpenHistories(memberId: Long) {
        val open = suspendRepo.findOpen(memberId)
        if (open.isEmpty()) return

        suspendRepo.saveAll(open.onEach(MemberSuspendHistory::release))
    }

    private fun findOrThrow(id: Long): Member =
        repo.find(id) ?: throw LanglezException(NOT_FOUND, "member.not-found")
}
