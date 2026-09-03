package com.langlez.block.application

import com.langlez.block.contract.MemberBlockedEvent
import com.langlez.block.domain.Block
import com.langlez.block.domain.BlockRepository
import com.langlez.block.domain.BlockRepository.Edge
import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberReader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

class BlockServiceTest : BehaviorSpec({

    val repo = mockk<BlockRepository>(relaxed = true)
    val members = mockk<MemberReader>()
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

    // 포트 판정을 트랜잭션 밖에서 끝내고 DB 만 TransactionTemplate 으로 감싼다. 테스트에선 그대로 실행시킨다.
    val tx = mockk<TransactionTemplate>()
    every { tx.execute<Any>(any()) } answers { firstArg<TransactionCallback<Any>>().doInTransaction(mockk(relaxed = true)) }

    val service = BlockService(repo, members, publisher, tx)

    afterEach { clearMocks(repo, members, publisher, answers = false) }

    fun member(id: Long) = MemberReader.ProfileInfo(
        id = id,
        handle = "user$id",
        gender = "SECRET",
        locale = null,
        birthDay = null,
        status = MemberReader.Status.ACTIVE,
    )

    Given("차단 요청 시") {

        When("자기 자신을 차단하면") {
            Then("400 이 나고 이벤트도 나가지 않는다") {
                every { members.findProfileInfo(1L) } returns member(1L)
                every { repo.find(1L, 1L) } returns null

                val ex = shouldThrow<LanglezException> { service.block(1L, 1L) }
                ex.status.value() shouldBe 400
                ex.message shouldBe "social.block.self"

                verify(exactly = 0) { publisher.publishEvent(any<Any>()) }
            }
        }

        When("없는 회원을 차단하면") {
            Then("404 가 난다") {
                every { members.findProfileInfo(99L) } returns null

                shouldThrow<LanglezException> { service.block(1L, 99L) }.status.value() shouldBe 404
            }
        }

        /**
         * 팔로우 해제는 여기서 하지 않는다. 팔로우 행은 follow 모듈 소유라
         * 이 이벤트를 받은 `FollowConsumer` 가 끊는다.
         */
        When("남을 차단하면") {
            Then("차단을 저장하고 차단 이벤트를 발행한다") {
                every { members.findProfileInfo(2L) } returns member(2L)
                every { repo.find(1L, 2L) } returns null

                val event = slot<MemberBlockedEvent>()
                every { publisher.publishEvent(capture(event)) } returns Unit

                service.block(1L, 2L)

                verify(exactly = 1) { repo.save(any<Block>()) }
                event.captured.blockerId shouldBe 1L
                event.captured.blockedId shouldBe 2L
            }
        }

        /**
         * 과거에 반쪽만 끊긴 데이터를 수습하는 경로다. 여기서 이벤트를 안 내보내면
         * 그 수습이 통째로 불가능해진다.
         */
        When("이미 차단한 상대를 다시 차단하면") {
            Then("중복 저장은 없지만 이벤트는 다시 나간다") {
                every { members.findProfileInfo(2L) } returns member(2L)
                every { repo.find(1L, 2L) } returns Block(1L, 2L)

                service.block(1L, 2L)

                verify(exactly = 0) { repo.save(any<Block>()) }
                verify(exactly = 1) { publisher.publishEvent(any<MemberBlockedEvent>()) }
            }
        }

        /**
         * `MessageDeduplicator` 는 페이로드 해시로 재배달을 가린다. occurredAt 이 비어 있거나
         * 고정값이면 위의 수습 이벤트가 첫 차단과 같은 페이로드가 되어 컨슈머 앞에서 걷어내진다.
         * 값이 실제로 채워지는지만 여기서 고정하고, 두 페이로드가 갈리는지는 FollowConsumerTest 가 본다.
         */
        When("이벤트를 열어 보면") {
            Then("occurredAt 이 요청 시각으로 채워져 있다") {
                every { members.findProfileInfo(2L) } returns member(2L)
                every { repo.find(1L, 2L) } returns null

                val before = Instant.now().toEpochMilli()

                val event = slot<MemberBlockedEvent>()
                every { publisher.publishEvent(capture(event)) } returns Unit

                service.block(1L, 2L)

                (event.captured.occurredAt >= before) shouldBe true
                (event.captured.occurredAt <= Instant.now().toEpochMilli()) shouldBe true
            }
        }
    }

    Given("차단 목록 조회 시") {

        When("탈퇴해서 사라진 회원이 섞여 있으면") {
            Then("그 항목은 빠지고 커서는 차단 행 id 로 내려간다") {
                every { repo.findBlocks(1L, 20, null) } returns listOf(
                    Edge(id = 30L, memberId = 2L),
                    Edge(id = 29L, memberId = 3L),
                )
                every { members.findProfileInfos(listOf(2L, 3L)) } returns mapOf(2L to member(2L))

                val views = service.listBlocks(1L, 20, null)

                views shouldHaveSize 1
                views[0].memberId shouldBe 2L
                views[0].cursor shouldBe 30L
            }
        }
    }
})
