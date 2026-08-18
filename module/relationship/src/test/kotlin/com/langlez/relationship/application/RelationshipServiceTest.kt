package com.langlez.relationship.application

import com.langlez.core.BlockQuery
import com.langlez.exception.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.domain.Report
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

class RelationshipServiceTest : BehaviorSpec({

    val repo = mockk<RelationshipRepository>(relaxed = true)
    val members = mockk<MemberRepository>()
    val blocks = mockk<BlockQuery>()
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

    val service = RelationshipService(repo, members, blocks, publisher)

    afterEach { clearMocks(repo, members, blocks, publisher, answers = false) }

    fun member(id: Long) = Member(
        id = id,
        email = "user$id@test.com",
        handle = "user$id",
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
    )

    Given("팔로우 요청 시") {

        When("자기 자신을 팔로우하면") {
            Then("400 이 난다") {
                every { members.find(1L) } returns member(1L)
                every { blocks.isBlockedBetween(any(), any()) } returns false
                every { repo.findFollow(any(), any()) } returns null

                val ex = shouldThrow<LanglezException> { service.follow(1L, 1L) }
                ex.status.value() shouldBe 400
                ex.message shouldBe "social.follow.self"
            }
        }

        When("차단 관계인 상대를 팔로우하면") {
            Then("403 이 나고 저장하지 않는다") {
                every { members.find(2L) } returns member(2L)
                every { blocks.isBlockedBetween(1L, 2L) } returns true

                val ex = shouldThrow<LanglezException> { service.follow(1L, 2L) }
                ex.status.value() shouldBe 403

                verify(exactly = 0) { repo.save(any<Follow>()) }
            }
        }

        When("없는 회원을 팔로우하면") {
            Then("404 가 난다") {
                every { members.find(99L) } returns null

                shouldThrow<LanglezException> { service.follow(1L, 99L) }.status.value() shouldBe 404
            }
        }

        When("이미 팔로우 중인 상대를 다시 팔로우하면") {
            Then("중복 저장도 중복 이벤트도 없다") {
                every { members.find(2L) } returns member(2L)
                every { blocks.isBlockedBetween(1L, 2L) } returns false
                every { repo.findFollow(1L, 2L) } returns Follow(1L, 2L)

                service.follow(1L, 2L)

                verify(exactly = 0) { repo.save(any<Follow>()) }
                verify(exactly = 0) { publisher.publishEvent(any<Any>()) }
            }
        }

        When("정상 팔로우하면") {
            Then("저장하고 팔로우 이벤트를 발행한다") {
                every { members.find(2L) } returns member(2L)
                every { blocks.isBlockedBetween(1L, 2L) } returns false
                every { repo.findFollow(1L, 2L) } returns null

                service.follow(1L, 2L)

                verify(exactly = 1) { repo.save(any<Follow>()) }
                verify(exactly = 1) { publisher.publishEvent(MemberFollowedEvent(1L, 2L)) }
            }
        }
    }

    Given("차단 요청 시") {

        When("자기 자신을 차단하면") {
            Then("400 이 난다") {
                every { members.find(1L) } returns member(1L)
                every { repo.findBlock(1L, 1L) } returns null

                val ex = shouldThrow<LanglezException> { service.block(1L, 1L) }
                ex.status.value() shouldBe 400
                ex.message shouldBe "social.block.self"
            }
        }

        When("남을 차단하면") {
            Then("차단을 저장하고 팔로우 관계를 양방향으로 끊는다") {
                every { members.find(2L) } returns member(2L)
                every { repo.findBlock(1L, 2L) } returns null

                service.block(1L, 2L)

                verify(exactly = 1) { repo.save(any<Block>()) }
                verify(exactly = 1) { repo.deleteFollow(1L, 2L) }
                verify(exactly = 1) { repo.deleteFollow(2L, 1L) }
            }
        }

        When("이미 차단한 상대를 다시 차단하면") {
            Then("중복 저장 없이 팔로우 해제만 다시 보장한다") {
                every { members.find(2L) } returns member(2L)
                every { repo.findBlock(1L, 2L) } returns Block(1L, 2L)

                service.block(1L, 2L)

                verify(exactly = 0) { repo.save(any<Block>()) }
                verify(exactly = 1) { repo.deleteFollow(1L, 2L) }
                verify(exactly = 1) { repo.deleteFollow(2L, 1L) }
            }
        }
    }

    Given("신고 접수 시") {

        When("같은 신고가 이미 있으면") {
            Then("저장하지 않는다 (카프카 재전달 대비 멱등)") {
                every { repo.existsReport(1L, Report.SourceType.CHAT_USER, "10", "m7") } returns true

                service.report(1L, 2L, Report.SourceType.CHAT_USER, "10", "욕설", "m7")

                verify(exactly = 0) { repo.save(any<Report>()) }
            }
        }

        When("처음 들어온 신고면") {
            Then("Report 로 저장한다") {
                every { repo.existsReport(1L, Report.SourceType.CHAT_USER, "10", "m7") } returns false

                val saved = slot<Report>()
                every { repo.save(capture(saved)) } answers { firstArg() }

                service.report(1L, 2L, Report.SourceType.CHAT_USER, "10", "욕설", "m7")

                saved.captured.reporterId shouldBe 1L
                saved.captured.reportedUserId shouldBe 2L
                saved.captured.sourceId shouldBe "10"
                saved.captured.triggerMessageId shouldBe "m7"
            }
        }
    }

    Given("팔로워 목록 조회 시") {

        When("탈퇴해서 사라진 회원이 섞여 있으면") {
            Then("그 항목은 빠지고 커서는 팔로우 행 id 로 내려간다") {
                every { repo.findFollowers(1L, 20, null) } returns listOf(
                    RelationshipRepository.Edge(id = 30L, memberId = 2L),
                    RelationshipRepository.Edge(id = 29L, memberId = 3L),
                )
                every { members.findAll(listOf(2L, 3L)) } returns listOf(member(2L))

                val views = service.listFollowers(1L, 20, null)

                views shouldHaveSize 1
                views[0].memberId shouldBe 2L
                views[0].cursor shouldBe 30L
            }
        }
    }
})
