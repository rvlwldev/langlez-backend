package com.langlez.relationship.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.langlez.core.BlockQuery
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.application.RelationshipService
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.domain.Report
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

class RelationshipConsumerTest : BehaviorSpec({

    val repo = mockk<RelationshipRepository>(relaxed = true)
    val members = mockk<MemberRepository>(relaxed = true)
    val blocks = mockk<BlockQuery>(relaxed = true)
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

    val service = RelationshipService(repo, members, blocks, publisher)
    val consumer = RelationshipConsumer(service, ObjectMapper().registerKotlinModule())

    afterEach { clearMocks(repo, members, blocks, publisher, answers = false) }

    val payload = """
        {"roomId":10,"reporterId":1,"reportedUserId":2,"reason":"욕설","triggerMessageId":"m7"}
    """.trimIndent()

    Given("chat-user-reported 이벤트를 받으면") {

        When("처음 받은 이벤트면") {
            Then("CHAT_USER 신고로 저장한다 (sourceId 는 방 id)") {
                every { repo.existsReport(1L, Report.SourceType.CHAT_USER, "10", "m7") } returns false

                consumer.onChatUserReported(payload)

                verify(exactly = 1) { repo.save(any<Report>()) }
            }
        }

        When("같은 이벤트가 다시 배달되면") {
            Then("두 번째부터는 저장하지 않는다 (at-least-once 대비)") {
                // 첫 배달 이후에는 같은 신고가 이미 있다.
                every { repo.existsReport(1L, Report.SourceType.CHAT_USER, "10", "m7") } returnsMany listOf(false, true)

                consumer.onChatUserReported(payload)
                consumer.onChatUserReported(payload)

                verify(exactly = 1) { repo.save(any<Report>()) }
            }
        }
    }
})
