package com.langlez.relationship.infrastructure

import com.langlez.relationship.contract.FollowQuery
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.infrastructure.jpa.BlockJpaRepository
import com.langlez.relationship.infrastructure.jpa.FollowJpaRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import com.querydsl.jpa.impl.JPAQueryFactory

/**
 * chat·echo 가 차단·팔로우를 보는 유일한 통로다.
 * 팔로우 조회가 죽으면 echo 홈 타임라인이 통째로 죽는다.
 */
class RelationshipQueryImplTest : BehaviorSpec({

    val blocks = mockk<BlockJpaRepository>()
    val follows = mockk<FollowJpaRepository>()
    val repo = mockk<RelationshipRepository>()
    // blockedAmong 은 QueryDSL 한 방이라 여기선 목이 의미가 없다. 실제 판정은
    // RelationshipRepositoryImplTest 가 진짜 DB 로 본다.
    val dsl = mockk<JPAQueryFactory>()
    val query = RelationshipQueryImpl(blocks, follows, repo, dsl)

    afterEach { clearMocks(blocks, follows, repo, answers = false) }

    /** 차단 행은 단방향으로만 저장된다. 그 한 방향만 true 로 두고 나머지는 전부 false 로 스텁한다. */
    fun blocked(blockerId: Long, blockedId: Long) {
        every { blocks.existsByBlockerIdAndBlockedId(any(), any()) } returns false
        every { blocks.existsByBlockerIdAndBlockedId(blockerId, blockedId) } returns true
    }

    Given("1번이 2번을 차단한 상태에서") {

        When("차단한 쪽(1번) 기준으로 조회하면") {
            blocked(blockerId = 1L, blockedId = 2L)

            Then("true 를 반환한다") {
                query.isBlockedBetween(1L, 2L) shouldBe true
            }
        }

        When("차단당한 쪽(2번) 기준으로 조회하면") {
            blocked(blockerId = 1L, blockedId = 2L)

            Then("역방향 조회도 true 를 반환한다") {
                query.isBlockedBetween(2L, 1L) shouldBe true
            }
        }

        When("차단과 무관한 쌍을 조회하면") {
            blocked(blockerId = 1L, blockedId = 2L)

            Then("false 를 반환한다") {
                query.isBlockedBetween(3L, 4L) shouldBe false
            }
        }
    }

    Given("내가 두 명을 팔로우하고 있으면") {
        every { follows.findAllByFollowerId(1L) } returns listOf(Follow(1L, 10L), Follow(1L, 20L))

        Then("그 id 들을 돌려준다") {
            query.followingIds(1L) shouldContainExactlyInAnyOrder listOf(10L, 20L)
        }
    }

    Given("팔로워 수와 팔로잉 수를 물으면") {
        every { repo.countFollowers(1L) } returns 12L
        every { repo.countFollowings(1L) } returns 3L

        Then("한 번의 호출로 두 숫자를 함께 돌려준다") {
            query.counts(1L) shouldBe FollowQuery.CountInfo(followers = 12L, followings = 3L)
        }
    }

    Given("아무도 팔로우하지 않으면") {
        every { follows.findAllByFollowerId(2L) } returns emptyList()

        Then("빈 목록이다") {
            query.followingIds(2L) shouldBe emptyList()
        }
    }
})
