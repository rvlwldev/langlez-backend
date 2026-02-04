package com.langlez.member.application

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRole
import com.langlez.member.domain.repository.MemberRepository
import io.kotest.core.spec.DisplayName
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

@DisplayName("Member: 회원 가입 및 조회 로직 테스트")
class MemberServiceTest : BehaviorSpec({
    val memberRepository = mockk<MemberRepository>()
    val memberService = MemberService(memberRepository)

    Given("기존 회원이 존재하는 경우") {
        val command = CreateMemberCommand(
            email = "test@example.com",
            nickname = "tester",
            profileImageUrl = "http://img.com/1.jpg",
            provider = "google",
            providerId = "12345"
        )
        val existingMember = Member(
            email = command.email,
            nickname = command.nickname,
            profileImageUrl = command.profileImageUrl,
            provider = command.provider,
            providerId = command.providerId
        )

        every { memberRepository.findByProviderAndProviderId("google", "12345") } returns existingMember

        When("회원 찾기 또는 생성을 요청하면") {
            val result = memberService.findOrCreateMember(command)

            Then("기존 회원을 반환하고 저장을 시도하지 않아야 한다") {
                result shouldBe existingMember
                verify(exactly = 0) { memberRepository.save(any()) }
            }
        }
    }

    Given("신규 회원인 경우") {
        val command = CreateMemberCommand(
            email = "new@example.com",
            nickname = "newuser",
            profileImageUrl = null,
            provider = "apple",
            providerId = "apple_123"
        )
        val newMember = Member(
            email = command.email,
            nickname = command.nickname,
            profileImageUrl = null,
            provider = command.provider,
            providerId = command.providerId
        )

        every { memberRepository.findByProviderAndProviderId("apple", "apple_123") } returns null
        every { memberRepository.save(any()) } returns newMember

        When("회원 찾기 또는 생성을 요청하면") {
            val result = memberService.findOrCreateMember(command)

            Then("새로운 회원을 생성하고 반환해야 한다") {
                result shouldNotBe null
                result.email shouldBe "new@example.com"
                result.role shouldBe MemberRole.MEMBER
                verify(exactly = 1) { memberRepository.save(any()) }
            }
        }
    }
})
