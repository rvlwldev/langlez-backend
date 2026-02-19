package com.langlez.member.api.rest.member

import com.langlez.common.security.SecurityUtils
import com.langlez.member.api.response.ProfileResponse
import com.langlez.member.application.MemberService
import com.langlez.member.domain.MemberProfileRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Member API", description = "회원 계정 API")
@RestController
@RequestMapping("/api/v1/members")
class MemberControllerV1(
    private val memberService: MemberService,
    private val profileRepo: MemberProfileRepository
) {

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    suspend fun getMe(): ProfileResponse {
        val email = SecurityUtils.getCurrentUserEmail()
        val member = memberService.getMember(email)
        val profile = profileRepo.findByMemberId(member.id)
        return ProfileResponse.from(member, profile)
    }

    @Operation(summary = "회원 조회 (@username)")
    @GetMapping("/@{username}")
    suspend fun getMemberByUsername(@PathVariable username: String): ProfileResponse {
        val member = memberService.getMemberByUsername(username)
        val profile = profileRepo.findByMemberId(member.id)
        return ProfileResponse.from(member, profile)
    }
}
