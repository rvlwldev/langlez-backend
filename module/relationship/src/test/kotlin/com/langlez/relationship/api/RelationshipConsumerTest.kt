package com.langlez.relationship.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.langlez.core.MessageDeduplicator
import com.langlez.member.contract.MemberQuery
import com.langlez.relationship.application.RelationshipService
import com.langlez.relationship.contract.BlockQuery
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.domain.Report
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

class RelationshipConsumerTest : BehaviorSpec({

    val repo = mockk<RelationshipRepository>(relaxed = true)
    val members = mockk<MemberQuery>(relaxed = true)
    val blocks = mockk<BlockQuery>(relaxed = true)
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

    // SETNX 를 흉내 내는 로컬 대역. relaxed 목을 쓰면 isDuplicate 가 늘 false 라
    // 중복 억제가 실제로 도는지 검증이 안 된다. 진짜 레디스 동작은 RedisMessageDeduplicatorTest 가 본다.
    val seen = mutableSetOf<String>()
    val dedup = object : MessageDeduplicator {
        override fun isDuplicate(topic: String, payload: String) = !seen.add(topic + payload)
        override fun release(topic: String, payload: String) {
            seen.remove(topic + payload)
        }
    }


    // 포트 판정을 트랜잭션 밖에서 끝내고 DB 만 TransactionTemplate 으로 감싼다. 테스트에선 그대로 실행시킨다.
    val tx = mockk<TransactionTemplate>()
    every { tx.execute<Any>(any()) } answers { firstArg<TransactionCallback<Any>>().doInTransaction(mockk(relaxed = true)) }

    val service = RelationshipService(repo, members, blocks, publisher, tx)
    val consumer = RelationshipConsumer(service, dedup, ObjectMapper().registerKotlinModule())

    afterEach {
        clearMocks(repo, members, blocks, publisher, answers = false)
        seen.clear()
    }

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
            Then("두 번째는 서비스까지 가지도 않는다") {
                // 존재 검사가 계속 false 여도 중복 검사에서 막혀야 한다.
                every { repo.existsReport(1L, Report.SourceType.CHAT_USER, "10", "m7") } returns false

                consumer.onChatUserReported(payload)
                consumer.onChatUserReported(payload)

                verify(exactly = 1) { repo.save(any<Report>()) }
            }
        }

        When("중복 검사를 통과했는데 저장이 실패하면") {
            Then("중복 표시를 되돌려 재시도가 다시 처리할 수 있게 한다") {
                every { repo.existsReport(any(), any(), any(), any()) } returns false

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
