package com.langlez.report.application

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.langlez.report.domain.Report
import com.langlez.report.domain.ReportRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class ReportEventListenerTest : BehaviorSpec({

    val reportRepository = mockk<ReportRepository>()
    val mapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val listener = ReportEventListener(reportRepository, mapper)

    afterEach {
        clearMocks(reportRepository)
    }

    Given("onEchoPostReported 호출 시") {
        When("정상적인 payload가 전달되면") {
            val payload = """
                {
                    "postId": "post-123",
                    "reporterId": 1,
                    "reportedUserId": 2,
                    "reason": "불쾌한 게시글"
                }
            """.trimIndent()

            val reportSlot = slot<Report>()
            every { reportRepository.save(capture(reportSlot)) } returns mockk()

            Then("ECHO_POST 타입으로 Report 엔티티가 저장된다") {
                listener.onEchoPostReported(payload)

                verify(exactly = 1) { reportRepository.save(any()) }
                reportSlot.captured.reporterId shouldBe 1L
                reportSlot.captured.reportedUserId shouldBe 2L
                reportSlot.captured.sourceType shouldBe Report.SourceType.ECHO_POST
                reportSlot.captured.sourceId shouldBe "post-123"
                reportSlot.captured.reason shouldBe "불쾌한 게시글"
                reportSlot.captured.triggerMessageId shouldBe null
            }
        }

        When("잘못된 형식의 payload가 전달되면") {
            val invalidPayload = "invalid json"

            Then("예외를 발생시키지 않고 무시한다") {
                listener.onEchoPostReported(invalidPayload)

                verify(exactly = 0) { reportRepository.save(any()) }
            }
        }
    }

    Given("onChatUserReported 호출 시") {
        When("정상적인 payload (triggerMessageId 포함)가 전달되면") {
            val payload = """
                {
                    "roomId": "room-456",
                    "reporterId": 10,
                    "reportedUserId": 20,
                    "reason": "욕설 및 비방",
                    "triggerMessageId": "msg-789"
                }
            """.trimIndent()

            val reportSlot = slot<Report>()
            every { reportRepository.save(capture(reportSlot)) } returns mockk()

            Then("CHAT_USER 타입으로 Report 엔티티가 저장된다") {
                listener.onChatUserReported(payload)

                verify(exactly = 1) { reportRepository.save(any()) }
                reportSlot.captured.reporterId shouldBe 10L
                reportSlot.captured.reportedUserId shouldBe 20L
                reportSlot.captured.sourceType shouldBe Report.SourceType.CHAT_USER
                reportSlot.captured.sourceId shouldBe "room-456"
                reportSlot.captured.reason shouldBe "욕설 및 비방"
                reportSlot.captured.triggerMessageId shouldBe "msg-789"
            }
        }

        When("잘못된 형식의 payload가 전달되면") {
            val invalidPayload = "invalid json"

            Then("예외를 발생시키지 않고 무시한다") {
                listener.onChatUserReported(invalidPayload)

                verify(exactly = 0) { reportRepository.save(any()) }
            }
        }
    }
})
