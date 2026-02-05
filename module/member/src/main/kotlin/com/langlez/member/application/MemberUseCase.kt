package com.langlez.member.application

import com.langlez.member.domain.LanguageLevel
import com.langlez.member.domain.Member

data class CreateMemberCommand(
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val provider: String,
    val providerId: String,
)

data class TargetLanguageCommandDto(
    val language: String,
    val level: LanguageLevel,
)

data class UpdateMemberCommand(
    val nickname: String,
    val profileImageUrl: String?,
    val additionalProfileImages: List<String>?,
    val locationCountry: String?,
    val locationCity: String?,
    val nationality: String?,
    val interests: List<String>?,
    val mbti: String?,
    val nativeLanguage: String?,
    val targetLanguages: List<TargetLanguageCommandDto>?,
    val wishDestinations: List<String>?,
    val visitedDestinations: List<String>?,
)

interface MemberUseCase {
    fun findOrCreateMember(command: CreateMemberCommand): Member
    fun getMember(email: String): Member
}
