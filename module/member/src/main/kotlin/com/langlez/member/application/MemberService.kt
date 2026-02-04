package com.langlez.member.application

import com.langlez.member.domain.Member
import com.langlez.member.domain.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class MemberService(
    private val memberRepository: MemberRepository,
) : MemberUseCase {
    override fun findOrCreateMember(command: CreateMemberCommand): Member =
        memberRepository.findByProviderAndProviderId(command.provider, command.providerId)
            ?: createMember(command)

    private fun createMember(command: CreateMemberCommand): Member {
        // 닉네임 중복 방지 로직 등이 필요할 수 있으나, 초기에는 소셜 닉네임 그대로 사용하거나 랜덤 생성
        val newMember =
            Member(
                email = command.email,
                nickname = command.nickname,
                profileImageUrl = command.profileImageUrl,
                provider = command.provider,
                providerId = command.providerId,
            )
        return memberRepository.save(newMember)
    }
}
