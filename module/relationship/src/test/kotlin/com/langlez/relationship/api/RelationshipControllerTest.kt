package com.langlez.relationship.api

import com.langlez.relationship.application.RelationshipMemberView
import com.langlez.relationship.application.RelationshipService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class RelationshipControllerTest : BehaviorSpec({

    val service = mockk<RelationshipService>()
    val controller = RelationshipController(service)

    afterEach { clearMocks(service, answers = false) }

    fun view(memberId: Long) = RelationshipMemberView(memberId, memberId, "user$memberId", null)

    Given("남의 팔로워 목록 요청 시") {

        When("size 를 터무니없이 크게 보내면") {
            Then("상한 50 으로 깎여서 서비스로 간다") {
                every { service.listFollowersOf(1L, 2L, 50, null) } returns emptyList()

                controller.listFollowersOf(viewerId = 1L, targetId = 2L, size = 1_000_000, cursor = null)

                verify { service.listFollowersOf(1L, 2L, 50, null) }
            }
        }

        When("size 를 0 이하로 보내면") {
            Then("최소 1 로 올라간다") {
                every { service.listFollowingsOf(1L, 2L, 1, null) } returns emptyList()

                controller.listFollowingsOf(viewerId = 1L, targetId = 2L, size = 0, cursor = null)

                verify { service.listFollowingsOf(1L, 2L, 1, null) }
            }
        }

        // 경로의 {memberId} 는 조회 대상이고 요청자는 @MemberId 다. 둘을 바꿔 넘기면
        // 남의 프로필을 열었는데 자기 팔로워가 나온다 — 컴파일도 되고 테스트도 조용히 통과하는 종류라 고정한다.
        When("요청자와 조회 대상이 다르면") {
            Then("조회 대상 id 로 조회한다") {
                every { service.listFollowersOf(7L, 99L, 20, 30L) } returns listOf(view(3L))

                val result = controller.listFollowersOf(viewerId = 7L, targetId = 99L, size = 20, cursor = 30L)

                result.map { it.memberId } shouldBe listOf(3L)
                verify { service.listFollowersOf(viewerId = 7L, targetId = 99L, size = 20, cursor = 30L) }
            }
        }
    }
})
