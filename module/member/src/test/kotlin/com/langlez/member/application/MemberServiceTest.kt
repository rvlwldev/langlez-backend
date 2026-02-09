package com.langlez.member.application

import com.langlez.common.exception.LanglezException
import com.langlez.member.application.command.CreateMemberCommand
import com.langlez.member.domain.*
import com.langlez.member.domain.embedded.MemberProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.DisplayName
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

@DisplayName("MemberService: 회원 가입 및 조회 로직 테스트")
class MemberServiceTest : BehaviorSpec({
    val repo = mockk<MemberRepository>()
    val service = MemberService(repo)

    Given("기존 회원이 provider로 존재하는 경우") {
        val command = CreateMemberCommand(
            email = "test@example.com",
            nickname = "tester",
            agreeTerm = false,
            providerId = "google_12345",
            providerType = MemberProvider.Type.GOOGLE,
            providerUserName = "Test User"
        )
        val existingMember = mockk<Member>(relaxed = true) {
            every { email } returns command.email
            every { nickname } returns command.nickname
            every { role } returns Member.Role.MEMBER
            every { audit } returns mockk(relaxed = true)
        }

        every { repo.findByProvider("google_12345", MemberProvider.Type.GOOGLE) } returns existingMember
        every { repo.save(any()) } returns existingMember

        When("회원 찾기 또는 생성을 요청하면") {
            val result = service.findOrCreateMember(command)

            Then("기존 회원을 반환하고 로그인 처리해야 한다") {
                result shouldBe existingMember
                verify(exactly = 1) { existingMember.login() }
                verify(exactly = 1) { repo.findByProvider("google_12345", MemberProvider.Type.GOOGLE) }
            }
        }
    }

    Given("기존 회원이 이메일로만 존재하는 경우") {
        val command = CreateMemberCommand(
            email = "existing@example.com",
            nickname = "existing",
            agreeTerm = false,
            providerId = "apple_999",
            providerType = MemberProvider.Type.APPLE,
            providerUserName = "Existing User"
        )

        val existingMember = mockk<Member>(relaxed = true) {
            every { email } returns command.email
        }

        every { repo.findByProvider("apple_999", MemberProvider.Type.APPLE) } returns null
        every { repo.findByEmail(command.email) } returns existingMember
        every { repo.save(any()) } returns existingMember

        When("회원 찾기 또는 생성을 요청하면") {
            val result = service.findOrCreateMember(command)

            Then("기존 회원을 반환하고 로그인 처리해야 한다") {
                result shouldBe existingMember
                verify(exactly = 1) { existingMember.login() }
            }
        }
    }

    Given("신규 회원인 경우") {
        val command =
            CreateMemberCommand(
                email = "new@example.com",
                nickname = "newuser",
                agreeTerm = false,
                providerId = "apple_123",
                providerType = MemberProvider.Type.APPLE,
                providerUserName = "New User"
            )

        every { repo.findByProvider("apple_123", MemberProvider.Type.APPLE) } returns null
        every { repo.findByEmail(command.email) } returns null
        every { repo.save(any()) } answers { firstArg() }

        When("회원 찾기 또는 생성을 요청하면") {
            val result = service.findOrCreateMember(command)

            Then("새로운 회원을 생성하고 로그인 처리해야 한다") {
                result shouldNotBe null
                result.email shouldBe "new@example.com"
                result.nickname shouldBe "newuser"
                result.role shouldBe Member.Role.MEMBER
                result.init shouldBe false
                verify(exactly = 1) { repo.save(any()) }
            }
        }
    }

    Given("이메일로 회원 조회 시") {
        val member = mockk<Member> { every { email } returns "test@example.com" }

        every { repo.findByEmail("test@example.com") } returns member

        When("존재하는 이메일로 조회하면") {
            val result = service.getMember("test@example.com")
            Then("회원을 반환해야 한다") { result shouldBe member }
        }
    }

    Given("존재하지 않는 이메일로 조회 시") {
        every { repo.findByEmail("notfound@example.com") } returns null

        When("조회를 시도하면") {
            Then("LanglezException을 던져야 한다") {
                shouldThrow<LanglezException> { service.getMember("notfound@example.com") }
            }
        }
    }

    Given("handle로 회원 조회 시") {
        val member = mockk<Member> { every { handle } returns "langlez_user" }

        every { repo.findByHandle("langlez_user") } returns member

        When("존재하는 handle로 조회하면") {
            val result = service.getMemberByHandle("langlez_user")

            Then("회원을 반환해야 한다") {
                result shouldBe member
                result.handle shouldBe "langlez_user"
            }
        }
    }
})
