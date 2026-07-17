package com.langlez.relationship.application

import com.langlez.core.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.outbox.RelationshipOutBoxRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*

class RelationshipServiceTest : BehaviorSpec({

    val repo = mockk<RelationshipRepository>()
    val memberRepo = mockk<MemberRepository>()
    val outbox = mockk<RelationshipOutBoxRepository>(relaxed = true)

    val service = RelationshipService(repo, memberRepo, outbox)

    afterEach { clearMocks(repo, memberRepo, outbox, answers = false) }

    fun createMember(id: Long, username: String = "user$id", nickname: String = "User $id") = Member(
        id = id,
        email = "$username@example.com",
        username = username,
        nickname = nickname,
        provider = MemberProvider("p$id", MemberProvider.Type.GOOGLE, nickname)
    )

    Given("팔로우 요청 시") {
        val followerId = 1L
        val followingId = 2L

        When("정상적으로 팔로우하면") {
            every { repo.findBlock(followingId, followerId) } returns null
            every { repo.findBlock(followerId, followingId) } returns null
            every { repo.saveFollow(any()) } returns Follow(id = 10, followerId = followerId, followedId = followingId)
            every { outbox.save(any(), any(), any(), any()) } returns mockk(relaxed = true)

            Then("팔로우가 저장되고 이벤트가 발행된다") {
                service.follow(followerId, followingId)
                verify { repo.saveFollow(match { it.followerId == followerId && it.followedId == followingId }) }
                verify {
                    outbox.save(
                        "RELATIONSHIP", "10", "MEMBER_FOLLOW",
                        match<RelationshipEvent.Follow> { it.followerId == followerId && it.followingId == followingId }
                    )
                }
            }
        }

        When("자기 자신을 팔로우하면") {
            Then("BAD_REQUEST 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.follow(1L, 1L)
                }
                ex.status shouldBe 400
                ex.message shouldBe "social.follow.self"
            }
        }

        When("상대방이 나를 차단한 상태이면") {
            every { repo.findBlock(followingId, followerId) } returns Block(id = 5, blockerId = followingId, blockedId = followerId)

            Then("FORBIDDEN 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.follow(followerId, followingId)
                }
                ex.status shouldBe 403
            }
        }

        When("내가 상대방을 차단한 상태이면") {
            every { repo.findBlock(followingId, followerId) } returns null
            every { repo.findBlock(followerId, followingId) } returns Block(id = 6, blockerId = followerId, blockedId = followingId)

            Then("FORBIDDEN 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.follow(followerId, followingId)
                }
                ex.status shouldBe 403
            }
        }
    }

    Given("언팔로우 요청 시") {
        val followerId = 1L
        val followingId = 2L

        When("팔로우 관계가 존재하면") {
            every { repo.findFollow(followerId, followingId) } returns Follow(id = 10, followerId = followerId, followedId = followingId)
            every { repo.deleteFollow(followerId, followingId) } just runs
            every { outbox.save(any(), any(), any(), any()) } returns mockk(relaxed = true)

            Then("팔로우가 삭제되고 이벤트가 발행된다") {
                service.unfollow(followerId, followingId)
                verify { repo.deleteFollow(followerId, followingId) }
                verify {
                    outbox.save(
                        "RELATIONSHIP", "10", "MEMBER_UNFOLLOW",
                        match<RelationshipEvent.Unfollow> { it.followerId == followerId && it.followingId == followingId }
                    )
                }
            }
        }

        When("팔로우 관계가 없으면") {
            every { repo.findFollow(followerId, followingId) } returns null

            Then("아무 동작 없이 정상 반환한다") {
                shouldNotThrowAny { service.unfollow(followerId, followingId) }
                verify(exactly = 0) { repo.deleteFollow(any(), any()) }
            }
        }
    }

    Given("차단 요청 시") {
        val blockerId = 1L
        val blockedId = 2L

        When("정상적으로 차단하면") {
            every { repo.saveBlock(any()) } returns Block(id = 20, blockerId = blockerId, blockedId = blockedId)
            every { repo.deleteFollow(blockerId, blockedId) } just runs
            every { repo.deleteFollow(blockedId, blockerId) } just runs
            every { outbox.save(any(), any(), any(), any()) } returns mockk(relaxed = true)

            Then("차단 저장, 양방향 팔로우 삭제, 이벤트 발행이 모두 수행된다") {
                service.block(blockerId, blockedId)
                verify { repo.saveBlock(match { it.blockerId == blockerId && it.blockedId == blockedId }) }
                verify { repo.deleteFollow(blockerId, blockedId) }
                verify { repo.deleteFollow(blockedId, blockerId) }
                verify {
                    outbox.save(
                        "RELATIONSHIP", "20", "MEMBER_BLOCK",
                        match<RelationshipEvent.Block> { it.blockerId == blockerId && it.blockedId == blockedId }
                    )
                }
            }
        }
    }

    Given("차단 해제 요청 시") {
        val blockerId = 1L
        val blockedId = 2L

        When("차단 관계가 존재하면") {
            every { repo.findBlock(blockerId, blockedId) } returns Block(id = 20, blockerId = blockerId, blockedId = blockedId)
            every { repo.deleteBlock(blockerId, blockedId) } just runs
            every { outbox.save(any(), any(), any(), any()) } returns mockk(relaxed = true)

            Then("차단이 삭제되고 이벤트가 발행된다") {
                service.unblock(blockerId, blockedId)
                verify { repo.deleteBlock(blockerId, blockedId) }
                verify {
                    outbox.save(
                        "RELATIONSHIP", "20", "MEMBER_UNBLOCK",
                        match<RelationshipEvent.Unblock> { it.blockerId == blockerId && it.blockedId == blockedId }
                    )
                }
            }
        }

        When("차단 관계가 없으면") {
            every { repo.findBlock(blockerId, blockedId) } returns null

            Then("아무 동작 없이 정상 반환한다") {
                shouldNotThrowAny { service.unblock(blockerId, blockedId) }
                verify(exactly = 0) { repo.deleteBlock(any(), any()) }
            }
        }
    }

    Given("팔로잉 목록 조회 시") {
        val followerId = 1L

        When("팔로잉이 존재하면") {
            val follows = listOf(
                Follow(id = 30, followerId = followerId, followedId = 2L),
                Follow(id = 20, followerId = followerId, followedId = 3L),
                Follow(id = 10, followerId = followerId, followedId = 4L),
            )
            val members = listOf(createMember(2L), createMember(3L), createMember(4L))

            every { repo.findFollowings(followerId, null, 20) } returns follows
            every { memberRepo.findByIds(listOf(2L, 3L, 4L)) } returns members

            Then("username/nickname 기반 목록이 반환된다") {
                val result = service.getFollowings(followerId, null, 20)
                result.members shouldHaveSize 3
                result.members[0].username shouldBe "user2"
                result.members[1].username shouldBe "user3"
            }
        }

        When("결과 수가 size와 같으면") {
            val follows = listOf(
                Follow(id = 30, followerId = followerId, followedId = 5L),
                Follow(id = 20, followerId = followerId, followedId = 4L),
                Follow(id = 10, followerId = followerId, followedId = 3L),
            )
            val members = follows.map { createMember(it.followedId) }

            every { repo.findFollowings(followerId, null, 3) } returns follows
            every { memberRepo.findByIds(listOf(5L, 4L, 3L)) } returns members

            Then("nextCursor는 마지막 Follow 엔티티의 ID이다") {
                val result = service.getFollowings(followerId, null, 3)
                result.nextCursor shouldBe 10L
            }
        }

        When("결과 수가 size보다 적으면") {
            val follows = listOf(
                Follow(id = 20, followerId = followerId, followedId = 5L),
            )
            val members = listOf(createMember(5L))

            every { repo.findFollowings(followerId, null, 20) } returns follows
            every { memberRepo.findByIds(listOf(5L)) } returns members

            Then("nextCursor는 null이다") {
                val result = service.getFollowings(followerId, null, 20)
                result.nextCursor shouldBe null
            }
        }
    }

    Given("차단 목록 조회 시") {
        val blockerId = 1L

        When("차단한 사용자가 존재하면") {
            val blocks = listOf(
                Block(id = 50, blockerId = blockerId, blockedId = 10L),
                Block(id = 40, blockerId = blockerId, blockedId = 11L),
            )
            val members = listOf(createMember(10L), createMember(11L))

            every { repo.findBlocks(blockerId, null, 20) } returns blocks
            every { memberRepo.findByIds(listOf(10L, 11L)) } returns members

            Then("차단 목록이 반환된다") {
                val result = service.getBlocks(blockerId, null, 20)
                result.members shouldHaveSize 2
                result.members[0].username shouldBe "user10"
            }
        }
    }
})
