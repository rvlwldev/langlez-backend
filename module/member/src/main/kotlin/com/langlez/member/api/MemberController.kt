package com.langlez.member.api

import com.langlez.member.application.MemberService
import com.langlez.member.application.UpdateMemberCommand
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/members")
class MemberController(
    private val memberService: MemberService,
) {
    @GetMapping("/me")
    fun getMyProfile(
        @AuthenticationPrincipal email: String,
    ): ResponseEntity<MemberResponse> {
        val member = memberService.getMember(email)
        return ResponseEntity.ok(MemberResponse.from(member))
    }

    @PutMapping("/me")
    fun updateMyProfile(
        @AuthenticationPrincipal email: String,
        @RequestBody @Valid request: UpdateMemberRequest,
    ): ResponseEntity<MemberResponse> {
        val command = UpdateMemberCommand(
            nickname = request.nickname,
            profileImageUrl = request.profileImageUrl,
            additionalProfileImages = request.additionalProfileImages,
            locationCountry = request.locationCountry,
            locationCity = request.locationCity,
            nationality = request.nationality,
            interests = request.interests,
            mbti = request.mbti,
            nativeLanguage = request.nativeLanguage,
            targetLanguages = request.targetLanguages?.map { it.toCommand() },
            wishDestinations = request.wishDestinations,
            visitedDestinations = request.visitedDestinations,
        )
        val member = memberService.updateMember(email, command)
        return ResponseEntity.ok(MemberResponse.from(member))
    }
}
