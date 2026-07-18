package com.langlez.relationship.api

import com.langlez.core.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.application.RelationshipService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*

class RelationshipControllerTest : BehaviorSpec({

    val service = mockk<RelationshipService>()
    val memberRepo = mockk<MemberRepository>()

    val controller = RelationshipController(service, memberRepo)

    afterEach { clearMocks(service, memberRepo, answers = false) }

    fun createMember(id: Long, username: String = "user$id", nickname: String = "User $id") = Member(
        id = id,
        email = "$username@example.com",
        username = username,
        nickname = nickname,
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
        providerDisplayName = nickname
    )

    Given("팔로우 요청 시") {
        When("username으로 팔로우하면") {
            val target = createMember(2L, "target_user")
            every { memberRepo.findByUsername("target_user") } returns target
            every { service.follow(1L, 2L) } just runs

            Then("username이 ID로 변환되어 서비스가 호출된다") {
                controller.follow(1L, "target_user")
                verify { service.follow(1L, 2L) }
            }
        }

        When("존재하지 않는 username이면") {
            every { memberRepo.findByUsername("ghost") } returns null

            Then("NOT_FOUND 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    controller.follow(1L, "ghost")
                }.status shouldBe 404
            }
        }
    }

    Given("팔로잉 목록 조회 시") {
        When("서비스가 정상 처리하면") {
            val cursorList = RelationshipResponse.CursorList(
                nextCursor = 10L,
                members = listOf(
                    RelationshipResponse.MemberSummary("user2", "User 2"),
                    RelationshipResponse.MemberSummary("user3", "User 3"),
                )
            )
            every { service.getFollowings(1L, null, 20) } returns cursorList

            Then("서비스 결과가 그대로 반환된다") {
                val result = controller.getFollowings(1L, null, 20)
                result.members shouldHaveSize 2
                result.nextCursor shouldBe 10L
            }
        }
    }

    Given("차단 목록 조회 시") {
        When("서비스가 정상 처리하면") {
            val cursorList = RelationshipResponse.CursorList(
                nextCursor = null,
                members = listOf(
                    RelationshipResponse.MemberSummary("user10", "User 10"),
                )
            )
            every { service.getBlocks(1L, null, 20) } returns cursorList

            Then("서비스 결과가 그대로 반환된다") {
                val result = controller.getBlocks(1L, null, 20)
                result.members shouldHaveSize 1
                result.members[0].username shouldBe "user10"
            }
        }
    }
})
