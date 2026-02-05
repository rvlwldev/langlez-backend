package com.langlez.member.api

import com.langlez.member.domain.LanguageLevel
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRole
import com.langlez.member.domain.TargetLanguage

import com.langlez.member.application.TargetLanguageCommandDto
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class MemberResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val role: MemberRole,
    val additionalProfileImages: List<String>,
    val locationCountry: String?,
    val locationCity: String?,
    val nationality: String?,
    val interests: List<String>,
    val mbti: String?,
    val nativeLanguage: String?,
    val targetLanguages: List<TargetLanguageDto>,
    val wishDestinations: List<String>,
    val visitedDestinations: List<String>,
) {
    companion object {
        fun from(member: Member): MemberResponse =
            MemberResponse(
                id = member.id ?: throw IllegalStateException("Member ID must not be null"),
                email = member.email,
                nickname = member.nickname,
                profileImageUrl = member.profileImageUrl,
                role = member.role,
                additionalProfileImages = member.additionalProfileImages,
                locationCountry = member.locationCountry,
                locationCity = member.locationCity,
                nationality = member.nationality,
                interests = member.interests,
                mbti = member.mbti,
                nativeLanguage = member.nativeLanguage,
                targetLanguages = member.targetLanguages.map { TargetLanguageDto.from(it) },
                wishDestinations = member.wishDestinations,
                visitedDestinations = member.visitedDestinations,
            )
    }
}

data class TargetLanguageDto(
    val language: String,
    val level: LanguageLevel,
) {
    companion object {
        fun from(targetLanguage: TargetLanguage): TargetLanguageDto =
            TargetLanguageDto(
                language = targetLanguage.language,
                level = targetLanguage.level,
            )

        fun toDomain(dto: TargetLanguageDto): TargetLanguage =
            TargetLanguage(
                language = dto.language,
                level = dto.level,
            )
    }

    fun toCommand(): TargetLanguageCommandDto =
        TargetLanguageCommandDto(
            language = this.language,
            level = this.level,
        )
}

data class UpdateMemberRequest(
    @field:NotBlank(message = "Nickname cannot be blank")
    @field:Size(min = 2, max = 20, message = "Nickname must be between 2 and 20 characters")
    val nickname: String,
    val profileImageUrl: String?,
    val additionalProfileImages: List<String>?,
    val locationCountry: String?,
    val locationCity: String?,
    val nationality: String?,
    val interests: List<String>?,
    val mbti: String?,
    val nativeLanguage: String?,
    val targetLanguages: List<TargetLanguageDto>?,
    val wishDestinations: List<String>?,
    val visitedDestinations: List<String>?,
)


