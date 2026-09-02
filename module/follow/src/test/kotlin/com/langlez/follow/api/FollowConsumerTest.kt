package com.langlez.follow.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.langlez.block.contract.BlockReader
import com.langlez.core.MessageDeduplicator
import com.langlez.follow.application.FollowService
import com.langlez.follow.domain.FollowRepository
import com.langlez.member.contract.MemberReader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

/**
 * 차단 이벤트를 받아 팔로우를 끊는 경로.
 *
 * 이 컨슈머가 죽으면 차단해 놓고 상대 팔로잉 목록에 그대로 남는다.
 */
class FollowConsumerTest : BehaviorSpec({

    val repo = mockk<FollowRepository>(relaxed = true)
    val members = mockk<MemberReader>(relaxed = true)
    val blocks = mockk<BlockReader>(relaxed = true)
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

    val tx = mockk<TransactionTemplate>()
    every { tx.execute<Any>(any()) } answers { firstArg<TransactionCallback<Any>>().doInTransaction(mockk(relaxed = true)) }

    val service = FollowService(repo, members, blocks, publisher, tx)
    val consumer = FollowConsumer(service, dedup, ObjectMapper().registerKotlinModule())

    afterEach {
        clearMocks(repo, members, blocks, publisher, answers = false)
        seen.clear()
    }

    val payload = """{"blockerId":1,"blockedId":2,"occurredAt":1700000000000}"""

    Given("member-blocked 이벤트를 받으면") {

        When("처음 받은 이벤트면") {
            Then("팔로우를 양방향으로 끊는다") {
                consumer.onMemberBlocked(payload)

                verify(exactly = 1) { repo.delete(1L, 2L) }
                verify(exactly = 1) { repo.delete(2L, 1L) }
            }
        }

        When("같은 이벤트가 다시 배달되면") {
            Then("두 번째는 서비스까지 가지도 않는다") {
                consumer.onMemberBlocked(payload)
                consumer.onMemberBlocked(payload)

                verify(exactly = 1) { repo.delete(1L, 2L) }
                verify(exactly = 1) { repo.delete(2L, 1L) }
            }
        }

        /**
         * 이미 차단한 상대를 다시 차단하는 수습 경로다. occurredAt 이 달라 페이로드가 갈리므로
         * 중복 검사에 걸리지 않는다 — 이 필드가 없으면 수습이 통째로 걷어내진다.
         */
        When("같은 쌍이지만 occurredAt 이 다른 이벤트가 오면") {
            Then("두 번 다 처리한다") {
                consumer.onMemberBlocked(payload)
                consumer.onMemberBlocked("""{"blockerId":1,"blockedId":2,"occurredAt":1700000009999}""")

                verify(exactly = 2) { repo.delete(1L, 2L) }
                verify(exactly = 2) { repo.delete(2L, 1L) }
            }
        }

        When("중복 검사를 통과했는데 삭제가 실패하면") {
            Then("중복 표시를 되돌려 재시도가 다시 처리할 수 있게 한다") {
                var attempts = 0
                every { repo.delete(1L, 2L) } answers {
                    attempts++
                    if (attempts == 1) throw IllegalStateException("DB 장애")
                }

                shouldThrow<IllegalStateException> { consumer.onMemberBlocked(payload) }

                consumer.onMemberBlocked(payload)

                verify(exactly = 2) { repo.delete(1L, 2L) }
            }
        }

        // 깨진 페이로드를 밖에서 풀면 표시가 남은 채 예외가 나가 그 이벤트가 통째로 유실된다.
        When("페이로드가 깨져 있으면") {
            Then("표시를 되돌리고 예외를 올린다") {
                shouldThrow<Exception> { consumer.onMemberBlocked("{ not json") }

                // 되돌아갔으므로 같은 페이로드가 다시 와도 중복으로 걸리지 않는다.
                shouldThrow<Exception> { consumer.onMemberBlocked("{ not json") }
            }
        }
    }
})
