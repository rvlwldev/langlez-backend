package com.langlez.member.api

import com.langlez.annotation.MemberId
import com.langlez.exception.LanglezException
import com.langlez.member.api.response.MemberMeResponse
import com.langlez.member.api.response.MemberOnlineStatusResponse
import com.langlez.member.api.response.MemberPublicResponse
import com.langlez.member.api.request.MemberUpdateFcmTokenRequest
import com.langlez.member.api.request.MemberUpdateNicknameRequest
import com.langlez.member.api.request.MemberUpdateUsernameRequest
import com.langlez.member.application.MemberService
import com.langlez.member.domain.MemberRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/members")
class MemberController(private val service: MemberService, private val repo: MemberRepository) {

    @GetMapping("/me")
    fun getMe(@MemberId memberId: Long): MemberMeResponse {
        val member = repo.find(memberId) ?: throw LanglezException(404, "member.not-found")
        return MemberMeResponse(member)
    }

    @PatchMapping("/me/username")
    fun patchUsername(
        @MemberId memberId: Long,
        @RequestBody @Valid request: MemberUpdateUsernameRequest
    ): MemberMeResponse {
        val member = service.updateUsername(memberId, request.username)
        return MemberMeResponse(member)
    }

    @PatchMapping("/me/nickname")
    fun patchNickname(
        @MemberId memberId: Long,
        @RequestBody @Valid request: MemberUpdateNicknameRequest
    ): MemberMeResponse {
        val member = service.updateNickname(memberId, request.nickname)
        return MemberMeResponse(member)
    }

    @PatchMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun patchFcmToken(@MemberId memberId: Long, @RequestBody @Valid request: MemberUpdateFcmTokenRequest) {
        service.updateFcmToken(memberId, request.token)
    }

    @GetMapping("/{username}")
    fun getMember(@PathVariable username: String): MemberPublicResponse {
        val member = repo.find(username) ?: throw LanglezException(404, "member.not-found")
        return MemberPublicResponse(member)
    }

    @GetMapping("/{username}/online-status")
    fun getOnlineStatus(@PathVariable username: String): MemberOnlineStatusResponse {
        val online = service.isOnline(username)
        return MemberOnlineStatusResponse(online)
    }
}