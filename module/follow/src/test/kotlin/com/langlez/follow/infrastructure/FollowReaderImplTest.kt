package com.langlez.follow.infrastructure

import com.langlez.follow.contract.FollowReader
import com.langlez.follow.domain.Follow
import com.langlez.follow.domain.FollowRepository
import com.langlez.follow.infrastructure.jpa.FollowJpaRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk

/**
 * echo·profile 이 팔로우를 보는 유일한 통로다.
 * 이 조회가 죽으면 echo 홈 타임라인이 통째로 죽는다.
 */
class FollowReaderImplTest : BehaviorSpec({

    val jpa = mockk<FollowJpaRepository>()
    val repo = mockk<FollowRepository>()
    val reader = FollowReaderImpl(jpa, repo)

    afterEach { clearMocks(jpa, repo, answers = false) }

    Given("내가 두 명을 팔로우하고 있으면") {
        every { jpa.findAllByFollowerId(1L) } returns listOf(Follow(1L, 10L), Follow(1L, 20L))

        Then("그 id 들을 돌려준다") {
            reader.followingIds(1L) shouldContainExactlyInAnyOrder listOf(10L, 20L)
        }
    }

    Given("팔로워 수와 팔로잉 수를 물으면") {
        every { repo.countFollowers(1L) } returns 12L
        every { repo.countFollowings(1L) } returns 3L

        Then("한 번의 호출로 두 숫자를 함께 돌려준다") {
            reader.counts(1L) shouldBe FollowReader.CountInfo(followers = 12L, followings = 3L)
        }
    }

    Given("아무도 팔로우하지 않으면") {
        every { jpa.findAllByFollowerId(2L) } returns emptyList()

        Then("빈 목록이다") {
            reader.followingIds(2L) shouldBe emptyList()
        }
    }
})
