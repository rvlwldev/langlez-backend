package com.langlez.member.application

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRole
import com.langlez.member.domain.repository.MemberRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Member: 회원 가입 및 조회 로직 테스트")
class MemberServiceTest {
    private val memberRepository: MemberRepository = mockk()
    private val memberService = MemberService(memberRepository)

    @Test
    @DisplayName("기존 회원이 존재하면 해당 회원을 반환한다")
    fun `should return existing member if found`() {
        // Given
        val command =
            CreateMemberCommand(
                email = "test@example.com",
                nickname = "tester",
                profileImageUrl = "http://img.com/1.jpg",
                provider = "google",
                providerId = "12345",
            )
        val existingMember =
            Member(
                email = command.email,
                nickname = command.nickname,
                profileImageUrl = command.profileImageUrl,
                provider = command.provider,
                providerId = command.providerId,
            )

        every { memberRepository.findByProviderAndProviderId("google", "12345") } returns existingMember

        // When
        val result = memberService.findOrCreateMember(command)

        // Then
        assertEquals(existingMember, result)
        verify(exactly = 0) { memberRepository.save(any()) }
    }

    @Test
    @DisplayName("신규 회원이면 저장 후 반환한다")
    fun `should create and return new member if not found`() {
        // Given
        val command =
            CreateMemberCommand(
                email = "new@example.com",
                nickname = "newuser",
                profileImageUrl = null,
                provider = "apple",
                providerId = "apple_123",
            )
        val newMember =
            Member(
                email = command.email,
                nickname = command.nickname,
                profileImageUrl = null,
                provider = command.provider,
                providerId = command.providerId,
            )

        every { memberRepository.findByProviderAndProviderId("apple", "apple_123") } returns null
        every { memberRepository.save(any()) } returns newMember

        // When
        val result = memberService.findOrCreateMember(command)

        // Then
        assertNotNull(result)
        assertEquals("new@example.com", result.email)
        assertEquals(MemberRole.MEMBER, result.role)
        verify(exactly = 1) { memberRepository.save(any()) }
    }
}
