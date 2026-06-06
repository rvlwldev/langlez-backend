package com.langlez.member.api

import com.langlez.core.LanglezException
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.http.HttpStatus

class MemberControllerTest : BehaviorSpec({

    val service = mockk<MemberService>()
    val repo = mockk<MemberRepository>()
    val controller = MemberController(service, repo)

    afterEach { clearMocks(service, repo, answers = false) }

    fun createMember(
        id: Long = 1L,
        email: String = "test@example.com",
        username: String = "testuser",
        nickname: String = "Test User"
    ) = Member(
        id = id,
        email = email,
        username = username,
        nickname = nickname,
        provider = MemberProvider("g123", MemberProvider.Type.GOOGLE, nickname)
    )

    Given("내 정보 조회 시") {
        When("회원이 존재하면") {
            val member = createMember()
            every { repo.findById(1L) } returns member

            Then("ID를 제외한 내 정보가 반환된다") {
                val result = controller.getMe(1L)
                result.email shouldBe "test@example.com"
                result.username shouldBe "testuser"
                result.nickname shouldBe "Test User"
                result.role shouldBe "MEMBER"
            }
        }

        When("회원이 존재하지 않으면") {
            every { repo.findById(999L) } returns null

            Then("NOT_FOUND 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    controller.getMe(999L)
                }
                ex.status shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    Given("유저네임 변경 시") {
        When("서비스가 정상 처리하면") {
            val updated = createMember(username = "newuser123")
            every { service.updateUsername(1L, "newuser123") } returns updated

            Then("변경된 유저네임이 반환된다") {
                val request = MemberRequest.UpdateUsername(username = "newuser123")
                val result = controller.patchUsername(1L, request)
                result.username shouldBe "newuser123"
            }
        }
    }

    Given("닉네임 변경 시") {
        When("서비스가 정상 처리하면") {
            val updated = createMember(nickname = "New Name")
            every { service.updateNickname(1L, "New Name") } returns updated

            Then("변경된 닉네임이 반환된다") {
                val request = MemberRequest.UpdateNickname(nickname = "New Name")
                val result = controller.patchNickname(1L, request)
                result.nickname shouldBe "New Name"
            }
        }
    }

    Given("다른 회원 조회 시") {
        When("username으로 조회하면") {
            val member = createMember(id = 2L, username = "other", nickname = "Other User")
            every { repo.findByUsername("other") } returns member

            Then("ID를 제외한 공개 정보만 반환된다") {
                val result = controller.getMember("other")
                result.username shouldBe "other"
                result.nickname shouldBe "Other User"
                result.role shouldBe "MEMBER"
            }
        }

        When("존재하지 않는 username이면") {
            every { repo.findByUsername("ghost") } returns null

            Then("NOT_FOUND 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    controller.getMember("ghost")
                }
                ex.status shouldBe HttpStatus.NOT_FOUND
            }
        }
    }
})
