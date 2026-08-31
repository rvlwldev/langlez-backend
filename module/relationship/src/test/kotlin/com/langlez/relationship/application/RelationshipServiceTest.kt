package com.langlez.relationship.application

import com.langlez.exception.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.contract.BlockQuery
import com.langlez.relationship.contract.MemberFollowedEvent
import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.domain.RelationshipRepository.Edge
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
import org.springframework.dao.DataIntegrityViolationException

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

    Given("남의 프로필에서 팔로워/팔로잉 목록을 열 때") {

        When("차단 관계인 상대의 목록을 열면") {
            Then("403 이 나고 조회조차 하지 않는다") {
                every { blocks.isBlockedBetween(1L, 2L) } returns true

                val followers = shouldThrow<LanglezException> { service.listFollowersOf(1L, 2L, 20, null) }
                followers.status.value() shouldBe 403
                followers.message shouldBe "social.blocked"

                val followings = shouldThrow<LanglezException> { service.listFollowingsOf(1L, 2L, 20, null) }
                followings.status.value() shouldBe 403

                verify(exactly = 0) { repo.findFollowers(any(), any(), any()) }
                verify(exactly = 0) { repo.findFollowings(any(), any(), any()) }
            }
        }

        When("차단 관계가 아니면") {
            Then("요청자가 아니라 조회 대상의 목록을 읽는다") {
                every { blocks.isBlockedBetween(1L, 2L) } returns false
                every { repo.findFollowers(2L, 20, null) } returns listOf(Edge(10L, 3L))
                every { members.findAll(listOf(3L)) } returns listOf(member(3L))

                service.listFollowersOf(1L, 2L, 20, null).map { it.memberId } shouldBe listOf(3L)

                verify { repo.findFollowers(2L, 20, null) }
            }
        }
    }

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
                // relaxed 목이 돌려주는 Follow 는 id 가 0이라 이벤트 검증이 통과해버린다. 명시 스텁을 둔다.
                every { repo.save(any<Follow>()) } returns Follow(id = 77L, followerId = 1L, followedId = 2L)

                service.follow(1L, 2L)

                verify(exactly = 1) { repo.save(any<Follow>()) }
                // 저장된 팔로우 행 id 가 실려야 컨슈머가 재팔로우와 카프카 재배달을 구분한다.
                verify(exactly = 1) { publisher.publishEvent(MemberFollowedEvent(77L, 1L, 2L)) }
            }
        }

        When("언팔로우 후 같은 상대를 다시 팔로우하면") {
            Then("행 id 가 달라 이벤트도 다른 값이 된다") {
                every { members.find(2L) } returns member(2L)
                every { blocks.isBlockedBetween(1L, 2L) } returns false
                every { repo.findFollow(1L, 2L) } returns null
                every { repo.save(any<Follow>()) } returnsMany listOf(
                    Follow(id = 81L, followerId = 1L, followedId = 2L),
                    Follow(id = 82L, followerId = 1L, followedId = 2L),
                )

                service.follow(1L, 2L)
                service.unfollow(1L, 2L)
                service.follow(1L, 2L)

                verify(exactly = 1) { publisher.publishEvent(MemberFollowedEvent(81L, 1L, 2L)) }
                verify(exactly = 1) { publisher.publishEvent(MemberFollowedEvent(82L, 1L, 2L)) }
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

        /**
         * 존재 검사와 저장 사이에 같은 신고가 들어오면 UNQ_REPORT_IDENTITY 가 막는다.
         * 그 충돌은 에러가 아니라 "이미 접수됨"이다 — 올리면 컨슈머가 재시도를 다 쓰고 DLT 로 가고,
         * HTTP 는 두 번 누른 사용자에게 500 을 준다.
         */
        When("존재 검사를 통과했는데 저장에서 유니크 제약에 걸리면") {
            Then("예외를 밖으로 올리지 않는다") {
                every { repo.existsReport(1L, Report.SourceType.CHAT_USER, "10", "m7") } returns false
                every { repo.save(any<Report>()) } throws DataIntegrityViolationException("UNQ_REPORT_IDENTITY")

                service.report(1L, 2L, Report.SourceType.CHAT_USER, "10", "욕설", "m7")
            }
        }

        When("저장이 유니크 제약 외의 이유로 실패하면") {
            Then("그대로 올린다 (DB 장애를 성공으로 삼키면 신고가 조용히 사라진다)") {
                every { repo.existsReport(1L, Report.SourceType.CHAT_USER, "11", "m8") } returns false
                every { repo.save(any<Report>()) } throws IllegalStateException("커넥션 없음")

                shouldThrow<IllegalStateException> {
                    service.report(1L, 2L, Report.SourceType.CHAT_USER, "11", "욕설", "m8")
                }
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
