package com.langlez.relationship.infrastructure

import com.langlez.core.FollowQuery
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.infrastructure.jpa.FollowJpaRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * echo 홈 타임라인이 이 포트를 쓴다. 구현이 없으면 타임라인이 통째로 죽는다.
 */
class FollowQueryImplTest : BehaviorSpec({

    val jpa = mockk<FollowJpaRepository>()
    val repo = mockk<RelationshipRepository>()
    val query = FollowQueryImpl(jpa, repo)

    Given("내가 두 명을 팔로우하고 있으면") {
        every { jpa.findAllByFollowerId(1L) } returns listOf(Follow(1L, 10L), Follow(1L, 20L))

        Then("그 id 들을 돌려준다") {
            query.followingIds(1L) shouldContainExactlyInAnyOrder listOf(10L, 20L)
        }
    }

    Given("팔로워 수와 팔로잉 수를 물으면") {
        every { repo.countFollowers(1L) } returns 12L
        every { repo.countFollowings(1L) } returns 3L

        Then("한 번의 호출로 두 숫자를 함께 돌려준다") {
            query.counts(1L) shouldBe FollowQuery.Counts(followers = 12L, followings = 3L)
        }
    }

    Given("아무도 팔로우하지 않으면") {
        every { jpa.findAllByFollowerId(2L) } returns emptyList()

        Then("빈 목록이다") {
            query.followingIds(2L) shouldBe emptyList()
        }
    }
})
