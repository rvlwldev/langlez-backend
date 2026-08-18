package com.langlez.relationship.infrastructure

import com.langlez.relationship.domain.Follow
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
    val query = FollowQueryImpl(jpa)

    Given("내가 두 명을 팔로우하고 있으면") {
        every { jpa.findAllByFollowerId(1L) } returns listOf(Follow(1L, 10L), Follow(1L, 20L))

        Then("그 id 들을 돌려준다") {
            query.followingIds(1L) shouldContainExactlyInAnyOrder listOf(10L, 20L)
        }
    }

    Given("아무도 팔로우하지 않으면") {
        every { jpa.findAllByFollowerId(2L) } returns emptyList()

        Then("빈 목록이다") {
            query.followingIds(2L) shouldBe emptyList()
        }
    }
})
