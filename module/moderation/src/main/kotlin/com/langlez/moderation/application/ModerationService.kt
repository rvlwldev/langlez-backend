package com.langlez.moderation.application

import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberWriter
import com.langlez.moderation.domain.Report
import com.langlez.moderation.domain.ReportRepository
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운영자 창구. 신고를 분류·처리하고 회원 제재를 실행한다.
 *
 * 신고 접수([ReportService])와 나눈다 — 접수는 사용자·카프카가 부르고 이쪽은 운영자만 부른다.
 * 트랜잭션 규칙도 정반대다(접수는 유니크 충돌을 삼켜야 해서 일부러 트랜잭션이 없다).
 *
 * **신고 처리와 회원 제재를 한 호출로 묶지 않는다.** 묶으면 "정지는 됐는데 신고 상태 갱신 실패"
 * 같은 반쪽 상태가 생기고, 신고 없이 직접 발견해 정지하는 경우를 담지 못한다.
 */
@Service
class ModerationService(
    private val repo: ReportRepository,
    private val members: MemberWriter,
) {

    @Transactional(readOnly = true)
    fun findReports(
        status: Report.Status?,
        sourceType: Report.SourceType?,
        size: Int,
        cursor: Long?,
    ): List<Report> = repo.findAll(status, sourceType, size, cursor)

    @Transactional
    fun handleReport(id: Long, status: Report.Status, note: String?, actorId: Long): Report {
        val report = repo.find(id) ?: throw LanglezException(NOT_FOUND, "report.not-found")

        try {
            report.handle(status, note, actorId)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(BAD_REQUEST, e.message, e)
        }

        return repo.save(report)
    }

    /**
     * 회원 정지.
     *
     * `MemberWriter` 는 `*-api` 포트라 **트랜잭션 밖에서 부른다.** 지금은 같은 프로세스지만
     * 이 포트는 gRPC/HTTP 로 대체될 예정이라, 트랜잭션 안에 두면 그때 DB 커넥션을 쥔 채
     * 네트워크를 기다려 풀이 마른다. 여기서 열 DB 작업 자체가 없으므로 `@Transactional` 을 걸지 않는다.
     *
     * 자기 자신·운영자·탈퇴 회원 거부는 구현(member)이 한다. 여기서 다시 검사하면
     * 두 곳의 판정이 어긋나는 순간부터 어느 쪽이 진짜인지 알 수 없게 된다.
     */
    fun suspendMember(memberId: Long, reason: String?, days: Long?, actorId: Long) =
        members.suspend(memberId, reason, days, actorId)

    fun unsuspendMember(memberId: Long, actorId: Long) = members.unsuspend(memberId, actorId)
}
