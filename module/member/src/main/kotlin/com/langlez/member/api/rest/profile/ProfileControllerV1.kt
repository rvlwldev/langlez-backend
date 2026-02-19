package com.langlez.member.api.rest.profile

import com.langlez.common.security.SecurityUtils
import com.langlez.member.api.request.InitHandleNicknameRequestV1
import com.langlez.member.api.response.ProfileResponse
import com.langlez.member.application.MemberService
import com.langlez.member.application.ProfileService
import com.langlez.member.domain.MemberProfileRepository
import com.langlez.member.domain.embedded.MemberIntroduction
import com.langlez.member.domain.embedded.MemberLanguage
import com.langlez.member.domain.embedded.MemberLocation
import com.langlez.member.domain.embedded.MemberPersonality
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Tag(name = "Profile API", description = "회원 프로필 API")
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileControllerV1(
    private val memberService: MemberService,
    private val profileService: ProfileService,
    private val profileRepo: MemberProfileRepository
) {

    @Operation(summary = "사용자명(핸들) 및 닉네임 수정")
    @PutMapping("/me/username")
    suspend fun updateUsername(@Valid @RequestBody request: InitHandleNicknameRequestV1): ProfileResponse {
        val email = SecurityUtils.getCurrentUserEmail()
        val member = profileService.saveUsername(email, request.handle, request.nickname)
        val profile = profileRepo.findByMemberId(member.id)
        return ProfileResponse.from(member, profile)
    }

    @Operation(summary = "성향 정보 수정")
    @PutMapping("/me/personality")
    suspend fun updatePersonality(@Valid @RequestBody personality: MemberPersonality): ProfileResponse {
        val email = SecurityUtils.getCurrentUserEmail()
        val profile = profileService.savePersonality(email, personality)
        return ProfileResponse.from(profile.member, profile)
    }

    @Operation(summary = "위치 정보 수정")
    @PutMapping("/me/location")
    suspend fun updateLocation(@Valid @RequestBody location: MemberLocation): ProfileResponse {
        val email = SecurityUtils.getCurrentUserEmail()
        val profile = profileService.saveLocation(email, location)
        return ProfileResponse.from(profile.member, profile)
    }

    @Operation(summary = "자기소개 수정")
    @PutMapping("/me/introduction")
    suspend fun updateIntroduction(@Valid @RequestBody introduction: MemberIntroduction): ProfileResponse {
        val email = SecurityUtils.getCurrentUserEmail()
        val profile = profileService.saveIntroduction(email, introduction)
        return ProfileResponse.from(profile.member, profile)
    }

    @Operation(summary = "언어 정보 수정")
    @PutMapping("/me/languages")
    suspend fun updateLanguages(@Valid @RequestBody languages: List<MemberLanguage>): ProfileResponse {
        val email = SecurityUtils.getCurrentUserEmail()
        val profile = profileService.saveLanguages(email, languages)
        return ProfileResponse.from(profile.member, profile)
    }

    @Operation(summary = "프로필 이미지 수정")
    @PutMapping(path = ["/me/images"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun updateImages(
        @RequestPart("profileImage") profileImage: MultipartFile,
        @RequestPart(value = "otherImages", required = false) otherImages: List<MultipartFile>?
    ): ProfileResponse {
        val email = SecurityUtils.getCurrentUserEmail()
        val member = profileService.updateImages(email, profileImage, otherImages)
        val profile = profileRepo.findByMemberId(member.id)
        return ProfileResponse.from(member, profile)
    }
}
