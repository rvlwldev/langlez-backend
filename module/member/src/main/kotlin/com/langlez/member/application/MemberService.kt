package com.langlez.member.application

import com.langlez.member.domain.Member
import com.langlez.member.domain.repository.MemberRepository
import com.langlez.member.domain.TargetLanguage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import com.langlez.common.exception.EntityNotFoundException

@Service
@Transactional
class MemberService(
    private val memberRepository: MemberRepository,
) : MemberUseCase {
    override fun findOrCreateMember(command: CreateMemberCommand): Member =
        memberRepository.findByProviderAndProviderId(command.provider, command.providerId)
            ?: createMember(command)

    @Transactional(readOnly = true)
    override fun getMember(email: String): Member =
        memberRepository.findByEmail(email)
            ?: throw EntityNotFoundException("Member not found with email: $email")

    fun updateMember(email: String, command: UpdateMemberCommand): Member {
        val member = getMember(email)
        member.updateProfile(
            nickname = command.nickname,
            profileImageUrl = command.profileImageUrl,
            additionalProfileImages = command.additionalProfileImages,
            locationCountry = command.locationCountry,
            locationCity = command.locationCity,
            nationality = command.nationality,
            interests = command.interests,
            mbti = command.mbti,
            nativeLanguage = command.nativeLanguage,
            targetLanguages = command.targetLanguages?.map { TargetLanguage(it.language, it.level) },
            wishDestinations = command.wishDestinations,
            visitedDestinations = command.visitedDestinations,
        )
        return member
    }

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
