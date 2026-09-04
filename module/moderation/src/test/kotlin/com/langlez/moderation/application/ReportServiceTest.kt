package com.langlez.moderation.application

import com.langlez.exception.LanglezException
import com.langlez.moderation.domain.Report
import com.langlez.moderation.domain.ReportRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException

class ReportServiceTest : BehaviorSpec({

    val repo = mockk<ReportRepository>(relaxed = true)
    val service = ReportService(repo)

    afterEach { clearMocks(repo, answers = false) }

    Given("신고 접수 시") {

        When("같은 신고가 이미 있으면") {
            Then("저장하지 않는다 (카프카 재전달 대비 멱등)") {
                every { repo.exists(1L, Report.SourceType.CHAT_USER, "10", "m7") } returns true

                service.report(1L, 2L, Report.SourceType.CHAT_USER, "10", "욕설", "m7")

                verify(exactly = 0) { repo.save(any<Report>()) }
            }
        }

        When("처음 들어온 신고면") {
            Then("Report 로 저장한다") {
                every { repo.exists(1L, Report.SourceType.CHAT_USER, "10", "m7") } returns false

                val saved = slot<Report>()
                every { repo.save(capture(saved)) } answers { firstArg() }

                service.report(1L, 2L, Report.SourceType.CHAT_USER, "10", "욕설", "m7")

                saved.captured.reporterId shouldBe 1L
                saved.captured.reportedUserId shouldBe 2L
                saved.captured.sourceId shouldBe "10"
                saved.captured.triggerMessageId shouldBe "m7"
            }
        }

        /**
         * 존재 검사와 저장 사이에 같은 신고가 들어오면 UNQ_REPORT_IDENTITY 가 막는다.
         * 그 충돌은 에러가 아니라 "이미 접수됨"이다 — 올리면 컨슈머가 재시도를 다 쓰고 DLT 로 가고,
         * HTTP 는 두 번 누른 사용자에게 500 을 준다.
         */
        When("존재 검사를 통과했는데 저장에서 유니크 제약에 걸리면") {
            Then("예외를 밖으로 올리지 않는다") {
                every { repo.exists(1L, Report.SourceType.CHAT_USER, "10", "m7") } returns false
                every { repo.save(any<Report>()) } throws DataIntegrityViolationException("UNQ_REPORT_IDENTITY")

                service.report(1L, 2L, Report.SourceType.CHAT_USER, "10", "욕설", "m7")
            }
        }

        When("저장이 유니크 제약 외의 이유로 실패하면") {
            Then("그대로 올린다 (DB 장애를 성공으로 삼키면 신고가 조용히 사라진다)") {
                every { repo.exists(1L, Report.SourceType.CHAT_USER, "11", "m8") } returns false
                every { repo.save(any<Report>()) } throws IllegalStateException("커넥션 없음")

                shouldThrow<IllegalStateException> {
                    service.report(1L, 2L, Report.SourceType.CHAT_USER, "11", "욕설", "m8")
                }
            }
        }
    }

    Given("ReportWriter 계약으로 들어오면") {

        When("아는 종류 문자열이면") {
            Then("도메인 열거값으로 바꿔 저장한다") {
                every { repo.exists(1L, Report.SourceType.ECHO_POST, "12", null) } returns false

                val saved = slot<Report>()
                every { repo.save(capture(saved)) } answers { firstArg() }

                service.report(
                    reporterId = 1L,
                    reportedUserId = 2L,
                    sourceType = "ECHO_POST",
                    sourceId = "12",
                    reason = "스팸",
                )

                saved.captured.sourceType shouldBe Report.SourceType.ECHO_POST
            }
        }

        // 계약 경계에서 들어온 문자열이라 신뢰하지 않는다. 저장까지 가면 운영 큐가 오염된다.
        When("모르는 종류 문자열이면") {
            Then("400 이 나고 저장하지 않는다") {
                val ex = shouldThrow<LanglezException> {
                    service.report(
                        reporterId = 1L,
                        reportedUserId = 2L,
                        sourceType = "WHATEVER",
                        sourceId = "12",
                        reason = "스팸",
                    )
                }

                ex.status.value() shouldBe 400
                ex.message shouldBe "report.source-type.invalid"
                verify(exactly = 0) { repo.save(any<Report>()) }
            }
        }
    }
})
