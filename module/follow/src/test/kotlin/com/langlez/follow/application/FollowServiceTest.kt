package com.langlez.follow.application

import com.langlez.block.contract.BlockReader
import com.langlez.exception.LanglezException
import com.langlez.follow.contract.MemberFollowedEvent
import com.langlez.follow.domain.Follow
import com.langlez.follow.domain.FollowRepository
import com.langlez.follow.domain.FollowRepository.Edge
import com.langlez.member.contract.MemberReader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

class FollowServiceTest : BehaviorSpec({

    val repo = mockk<FollowRepository>(relaxed = true)
    val members = mockk<MemberReader>()
    val blocks = mockk<BlockReader>()
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

    // 포트 판정을 트랜잭션 밖에서 끝내고 DB 만 TransactionTemplate 으로 감싼다. 테스트에선 그대로 실행시킨다.
    val tx = mockk<TransactionTemplate>()
    every { tx.execute<Any>(any()) } answers { firstArg<TransactionCallback<Any>>().doInTransaction(mockk(relaxed = true)) }

    val service = FollowService(repo, members, blocks, publisher, tx)

    afterEach { clearMocks(repo, members, blocks, publisher, answers = false) }

    fun member(id: Long) = MemberReader.ProfileInfo(
        id = id,
        handle = "user$id",
        gender = "SECRET",
        locale = null,
        birthDay = null,
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
                every { members.findProfileInfos(listOf(3L)) } returns mapOf(3L to member(3L))
                every { blocks.blockedAmong(1L, listOf(3L)) } returns emptySet()

                service.listFollowersOf(1L, 2L, 20, null).map { it.memberId } shouldBe listOf(3L)

                verify { repo.findFollowers(2L, 20, null) }
            }
        }
    }

    Given("팔로우 요청 시") {

        When("자기 자신을 팔로우하면") {
            Then("400 이 난다") {
                every { members.findProfileInfo(1L) } returns member(1L)
                every { blocks.isBlockedBetween(any(), any()) } returns false
                every { repo.find(any(), any()) } returns null

                val ex = shouldThrow<LanglezException> { service.follow(1L, 1L) }
                ex.status.value() shouldBe 400
                ex.message shouldBe "social.follow.self"
            }
        }

        When("차단 관계인 상대를 팔로우하면") {
            Then("403 이 나고 저장하지 않는다") {
                every { members.findProfileInfo(2L) } returns member(2L)
                every { blocks.isBlockedBetween(1L, 2L) } returns true

                val ex = shouldThrow<LanglezException> { service.follow(1L, 2L) }
                ex.status.value() shouldBe 403

                verify(exactly = 0) { repo.save(any<Follow>()) }
            }
        }

        When("없는 회원을 팔로우하면") {
            Then("404 가 난다") {
                every { members.findProfileInfo(99L) } returns null

                shouldThrow<LanglezException> { service.follow(1L, 99L) }.status.value() shouldBe 404
            }
        }

        When("이미 팔로우 중인 상대를 다시 팔로우하면") {
            Then("중복 저장도 중복 이벤트도 없다") {
                every { members.findProfileInfo(2L) } returns member(2L)
                every { blocks.isBlockedBetween(1L, 2L) } returns false
                every { repo.find(1L, 2L) } returns Follow(1L, 2L)

                service.follow(1L, 2L)

                verify(exactly = 0) { repo.save(any<Follow>()) }
                verify(exactly = 0) { publisher.publishEvent(any<Any>()) }
            }
        }

        When("정상 팔로우하면") {
            Then("저장하고 팔로우 이벤트를 발행한다") {
                every { members.findProfileInfo(2L) } returns member(2L)
                every { blocks.isBlockedBetween(1L, 2L) } returns false
                every { repo.find(1L, 2L) } returns null
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
                every { members.findProfileInfo(2L) } returns member(2L)
                every { blocks.isBlockedBetween(1L, 2L) } returns false
                every { repo.find(1L, 2L) } returns null
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

    /**
     * 차단 시 팔로우 해제는 `member-blocked` 컨슈머가 이 메서드로 들어온다.
     * 한 방향만 지우면 차단해 놓고 상대 팔로잉 목록에 그대로 남는다.
     */
    Given("차단으로 팔로우를 끊을 때") {

        When("양방향 해제를 부르면") {
            Then("두 방향 모두 지운다") {
                service.unfollowBothWays(1L, 2L)

                verify(exactly = 1) { repo.delete(1L, 2L) }
                verify(exactly = 1) { repo.delete(2L, 1L) }
            }
        }

        When("같은 해제를 두 번 부르면") {
            Then("없는 관계를 지워도 예외가 없다 (재배달 대비 멱등)") {
                service.unfollowBothWays(1L, 2L)
                service.unfollowBothWays(1L, 2L)

                verify(exactly = 2) { repo.delete(1L, 2L) }
                verify(exactly = 2) { repo.delete(2L, 1L) }
            }
        }
    }

    Given("팔로워 목록 조회 시") {

        When("탈퇴해서 사라진 회원이 섞여 있으면") {
            Then("그 항목은 빠지고 커서는 팔로우 행 id 로 내려간다") {
                every { repo.findFollowers(1L, 20, null) } returns listOf(
                    Edge(id = 30L, memberId = 2L),
                    Edge(id = 29L, memberId = 3L),
                )
                every { members.findProfileInfos(listOf(2L, 3L)) } returns mapOf(2L to member(2L))
                every { blocks.blockedAmong(1L, listOf(2L, 3L)) } returns emptySet()

                val views = service.listFollowers(1L, 20, null)

                views shouldHaveSize 1
                views[0].memberId shouldBe 2L
                views[0].cursor shouldBe 30L
            }
        }
    }

    /**
     * 차단은 커밋 즉시 효력이 나야 하는데 팔로우 행 정리는 `member-blocked` 컨슈머가 할 때까지
     * 지연된다. 그 창 동안 목록 API 가 차단한 상대를 그대로 내보내면 안 된다.
     */
    Given("차단한 상대가 아직 팔로우 행에 남아 있을 때") {

        val edges = listOf(Edge(id = 30L, memberId = 2L), Edge(id = 29L, memberId = 3L))
        val infos = mapOf(2L to member(2L), 3L to member(3L))

        When("내 팔로워 목록을 열면") {
            Then("차단한 회원이 빠진다") {
                every { repo.findFollowers(1L, 20, null) } returns edges
                every { members.findProfileInfos(listOf(2L, 3L)) } returns infos
                every { blocks.blockedAmong(1L, listOf(2L, 3L)) } returns setOf(2L)

                service.listFollowers(1L, 20, null).map { it.memberId } shouldBe listOf(3L)
            }
        }

        When("내 팔로잉 목록을 열면") {
            Then("차단한 회원이 빠진다") {
                every { repo.findFollowings(1L, 20, null) } returns edges
                every { members.findProfileInfos(listOf(2L, 3L)) } returns infos
                every { blocks.blockedAmong(1L, listOf(2L, 3L)) } returns setOf(2L)

                service.listFollowings(1L, 20, null).map { it.memberId } shouldBe listOf(3L)
            }
        }

        When("남의 프로필에서 팔로워 목록을 열면") {
            Then("viewer 가 차단한 회원이 빠진다 — 판정 기준은 목록 주인이 아니라 viewer 다") {
                every { blocks.isBlockedBetween(1L, 9L) } returns false
                every { repo.findFollowers(9L, 20, null) } returns edges
                every { members.findProfileInfos(listOf(2L, 3L)) } returns infos
                every { blocks.blockedAmong(1L, listOf(2L, 3L)) } returns setOf(2L)

                service.listFollowersOf(1L, 9L, 20, null).map { it.memberId } shouldBe listOf(3L)
            }
        }

        When("남의 프로필에서 팔로잉 목록을 열면") {
            Then("viewer 가 차단한 회원이 빠진다") {
                every { blocks.isBlockedBetween(1L, 9L) } returns false
                every { repo.findFollowings(9L, 20, null) } returns edges
                every { members.findProfileInfos(listOf(2L, 3L)) } returns infos
                every { blocks.blockedAmong(1L, listOf(2L, 3L)) } returns setOf(2L)

                service.listFollowingsOf(1L, 9L, 20, null).map { it.memberId } shouldBe listOf(3L)
            }
        }

        When("목록이 비어 있으면") {
            Then("차단 포트를 부르지 않는다 — 빈 목록에 왕복을 쓸 이유가 없다") {
                every { repo.findFollowers(1L, 20, null) } returns emptyList()

                service.listFollowers(1L, 20, null) shouldBe emptyList()

                verify(exactly = 0) { blocks.blockedAmong(any(), any()) }
            }
        }
    }
})
