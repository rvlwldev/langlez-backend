package com.langlez.report.application

import com.langlez.exception.LanglezException
import com.langlez.report.contract.ReportWriter
import com.langlez.report.domain.Report
import com.langlez.report.domain.ReportRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.stereotype.Service

/**
 * 신고 유스케이스.
 *
 * `ReportWriter` 계약을 이 클래스가 직접 구현한다. 어댑터를 따로 두면 그 어댑터가
 * infrastructure 에서 application 을 참조하게 되어 의존 방향이 뒤집힌다.
 *
 * **회원 존재 확인을 하지 않는다.** 신고 대상이 그 사이 탈퇴해도 신고는 접수돼야 하고
 * (탈퇴 회원 추적이 신고의 목적 중 하나다), 카프카로 들어오는 채팅 신고 경로에서
 * 회원 조회 실패로 신고가 DLT 로 빠지면 그게 더 나쁘다.
 */
@Service
class ReportService(private val repo: ReportRepository) : ReportWriter {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * `ReportWriter` 계약 구현. 계약이 종류를 문자열로 받으므로 여기서 도메인 열거값으로 바꾼다 —
     * `Report.SourceType` 을 계약으로 끌어올리면 report 의 열거값이 전 모듈 공용 계약이 된다.
     */
    override fun report(
        reporterId: Long,
        reportedUserId: Long,
        sourceType: String,
        sourceId: String,
        reason: String,
        triggerMessageId: String?,
    ) = report(
        reporterId = reporterId,
        reportedUserId = reportedUserId,
        sourceType = toSourceType(sourceType),
        sourceId = sourceId,
        reason = reason,
        triggerMessageId = triggerMessageId,
    )

    /**
     * 신고 접수.
     *
     * 같은 신고가 이미 있으면 아무것도 하지 않는다(멱등). 카프카 재배달·클라이언트 재시도로
     * 같은 신고가 여러 행 쌓이면 운영자가 같은 건을 몇 번씩 처리하게 된다.
     *
     * **트랜잭션을 걸지 않는다.** 읽기 하나 + 쓰기 하나뿐이라 묶어도 얻는 게 없고,
     * 묶으면 오히려 아래 유니크 충돌을 삼킬 수 없다 — 하이버네이트가 제약 위반 시점에
     * 트랜잭션을 rollback-only 로 표시해서, 예외를 잡고 정상 반환해도 커밋에서
     * `UnexpectedRollbackException` 이 난다. `repo.save` 는 자기 트랜잭션을 갖는다.
     */
    fun report(
        reporterId: Long,
        reportedUserId: Long,
        sourceType: Report.SourceType,
        sourceId: String,
        reason: String,
        triggerMessageId: String? = null,
    ) {
        if (repo.exists(reporterId, sourceType, sourceId, triggerMessageId)) return

        try {
            repo.save(Report(reporterId, reportedUserId, sourceType, sourceId, reason, triggerMessageId))
        } catch (e: DataIntegrityViolationException) {
            // UNQ_REPORT_IDENTITY 충돌 = 위 검사와 저장 사이에 같은 신고가 들어왔다. 정상 상황이라 삼킨다.
            //
            // 두 호출 경로가 같은 결론이라 여기서 한 번만 흡수한다.
            // 컨슈머(chat-user-reported): 이미 저장돼 있으니 성공으로 보고 오프셋을 넘겨야 한다.
            //   올리면 재시도를 다 쓰고 DLT 로 간다 — 저장은 됐는데 사람이 DLT 를 뒤지게 된다.
            // HTTP(POST /reports): 두 번 눌러도 204 다. 접수 전에도 접수 후에도 응답이 같아야
            //   재시도가 안전하고, 이미 존재 검사가 걸렀을 때와 동작이 갈리지 않는다.
            //
            // 재시도하지 않는다(@Retryable 금지). 몇 번을 다시 넣어도 같은 행이 이미 있다.
            logger.debug("중복 신고를 무시한다. reporter={} source={}:{}", reporterId, sourceType, sourceId, e)
        }
    }

    /** 계약 경계에서 들어온 문자열이라 신뢰하지 않는다. 모르는 값이면 400 이다. */
    private fun toSourceType(sourceType: String): Report.SourceType =
        try {
            Report.SourceType.valueOf(sourceType)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(BAD_REQUEST, "report.source-type.invalid", e)
        }
}
