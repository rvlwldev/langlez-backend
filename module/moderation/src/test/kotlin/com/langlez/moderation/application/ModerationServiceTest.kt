package com.langlez.moderation.application

import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberWriter
import com.langlez.moderation.domain.Report
import com.langlez.moderation.domain.ReportRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ModerationServiceTest : BehaviorSpec({

    val repo = mockk<ReportRepository>(relaxed = true)
    val members = mockk<MemberWriter>(relaxed = true)
    val service = ModerationService(repo, members)

    afterEach { clearMocks(repo, members, answers = false) }

    fun report(id: Long = 1L) = Report(
        reporterId = 1L,
        reportedUserId = 2L,
        sourceType = Report.SourceType.ECHO_POST,
        sourceId = "10",
        reason = "스팸",
    ).apply { this.id = id }

    Given("신고 처리 시") {

        When("접수 상태인 신고를 조치함으로 바꾸면") {
            val target = report()
            every { repo.find(1L) } returns target
            every { repo.save(any()) } answers { firstArg() }

            service.handleReport(1L, Report.Status.ACTIONED, "7일 정지함", actorId = 7L)

            Then("상태와 메모, 처리자와 처리 시각이 남는다") {
                target.status shouldBe Report.Status.ACTIONED
                target.adminNote shouldBe "7일 정지함"
                target.handledBy shouldBe 7L
                target.handledAt.shouldNotBeNull()
            }
        }

        // 상태만 바꾸려는 요청이 앞선 운영자의 메모를 날리면 안 된다.
        When("메모 없이 상태만 바꾸면") {
            val target = report().apply { handle(Report.Status.REVIEWING, "먼저 남긴 메모", actorId = 6L) }
            every { repo.find(1L) } returns target
            every { repo.save(any()) } answers { firstArg() }

            service.handleReport(1L, Report.Status.DISMISSED, null, actorId = 7L)

            Then("기존 메모가 유지된다") {
                target.status shouldBe Report.Status.DISMISSED
                target.adminNote shouldBe "먼저 남긴 메모"
            }
        }

        // 접수 시점을 뜻하는 상태라 처리자·처리 시각이 채워진 행과 의미가 어긋난다.
        When("RECEIVED 로 되돌리려 하면") {
            every { repo.find(1L) } returns report()

            Then("400 이고 저장하지 않는다") {
                val ex = shouldThrow<LanglezException> {
                    service.handleReport(1L, Report.Status.RECEIVED, null, actorId = 7L)
                }

                ex.status.value() shouldBe 400
                ex.message shouldBe "report.status.invalid"
                verify(exactly = 0) { repo.save(any()) }
            }
        }

        When("없는 신고면") {
            every { repo.find(999L) } returns null

            Then("404 다") {
                val ex = shouldThrow<LanglezException> {
                    service.handleReport(999L, Report.Status.ACTIONED, null, actorId = 7L)
                }

                ex.status.value() shouldBe 404
                ex.message shouldBe "report.not-found"
            }
        }
    }

    Given("회원 제재 시") {

        /**
         * 정지와 신고 처리를 한 호출로 묶지 않는다. 묶으면 "정지는 됐는데 신고 상태 갱신 실패"
         * 같은 반쪽 상태가 생기고, 신고 없이 직접 발견해 정지하는 경우를 담지 못한다.
         */
        When("정지를 요청하면") {
            service.suspendMember(2L, "스팸", 7L, actorId = 7L)

            Then("포트로 넘기고 신고는 건드리지 않는다") {
                verify { members.suspend(2L, "스팸", 7L, 7L) }
                verify(exactly = 0) { repo.save(any()) }
            }
        }

        When("해제를 요청하면") {
            service.unsuspendMember(2L, actorId = 7L)

            Then("포트로 넘긴다") {
                verify { members.unsuspend(2L, 7L) }
            }
        }
    }
})
