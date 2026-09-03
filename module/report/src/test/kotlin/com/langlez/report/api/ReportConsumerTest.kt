package com.langlez.report.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.langlez.core.MessageDeduplicator
import com.langlez.report.application.ReportService
import com.langlez.report.domain.Report
import com.langlez.report.domain.ReportRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ReportConsumerTest : BehaviorSpec({

    val repo = mockk<ReportRepository>(relaxed = true)

    // SETNX 를 흉내 내는 로컬 대역. relaxed 목을 쓰면 isDuplicate 가 늘 false 라
    // 중복 억제가 실제로 도는지 검증이 안 된다. 진짜 레디스 동작은 RedisMessageDeduplicatorTest 가 본다.
    val seen = mutableSetOf<String>()
    val dedup = object : MessageDeduplicator {
        override fun isDuplicate(topic: String, payload: String) = !seen.add(topic + payload)
        override fun release(topic: String, payload: String) {
            seen.remove(topic + payload)
        }
    }

    val service = ReportService(repo)
    val consumer = ReportConsumer(service, dedup, ObjectMapper().registerKotlinModule())

    afterEach {
        clearMocks(repo, answers = false)
        seen.clear()
    }

    val payload = """
        {"roomId":10,"reporterId":1,"reportedUserId":2,"reason":"욕설","triggerMessageId":"m7"}
    """.trimIndent()

    Given("chat-user-reported 이벤트를 받으면") {

        When("처음 받은 이벤트면") {
            Then("CHAT_USER 신고로 저장한다 (sourceId 는 방 id)") {
                every { repo.exists(1L, Report.SourceType.CHAT_USER, "10", "m7") } returns false

                consumer.onChatUserReported(payload)

                verify(exactly = 1) { repo.save(any<Report>()) }
            }
        }

        When("같은 이벤트가 다시 배달되면") {
            Then("두 번째는 서비스까지 가지도 않는다") {
                // 존재 검사가 계속 false 여도 중복 검사에서 막혀야 한다.
                every { repo.exists(1L, Report.SourceType.CHAT_USER, "10", "m7") } returns false

                consumer.onChatUserReported(payload)
                consumer.onChatUserReported(payload)

                verify(exactly = 1) { repo.save(any<Report>()) }
            }
        }

        When("중복 검사를 통과했는데 저장이 실패하면") {
            Then("중복 표시를 되돌려 재시도가 다시 처리할 수 있게 한다") {
                every { repo.exists(any(), any(), any(), any()) } returns false

                var attempts = 0
                every { repo.save(any<Report>()) } answers {
                    attempts++
                    if (attempts == 1) throw IllegalStateException("DB 장애")
                    firstArg()
                }

                shouldThrow<IllegalStateException> { consumer.onChatUserReported(payload) }

                consumer.onChatUserReported(payload)

                verify(exactly = 2) { repo.save(any<Report>()) }
            }
        }
    }
})
