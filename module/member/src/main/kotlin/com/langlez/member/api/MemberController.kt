package com.langlez.member.api

import com.langlez.core.LanglezException
import com.langlez.member.application.MemberService
import com.langlez.member.domain.MemberRepository
import com.langlez.security.web.MemberID
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val service: MemberService,
    private val repo: MemberRepository
) {

    @GetMapping("/me")
    fun getMe(@MemberID memberId: Long): MemberResponse.Me {
        val member = repo.findById(memberId) ?: throw LanglezException(404, "member.not-found")
        return MemberResponse.Me(member)
    }

    @PatchMapping("/me/username")
    fun patchUsername(@MemberID memberId: Long, @RequestBody @Valid request: MemberRequest.UpdateUsername): MemberResponse.Me {
        val member = service.updateUsername(memberId, request.username)
        return MemberResponse.Me(member)
    }

    @PatchMapping("/me/nickname")
    fun patchNickname(@MemberID memberId: Long, @RequestBody @Valid request: MemberRequest.UpdateNickname): MemberResponse.Me {
        val member = service.updateNickname(memberId, request.nickname)
        return MemberResponse.Me(member)
    }

    @PatchMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun patchFcmToken(@MemberID memberId: Long, @RequestBody @Valid request: MemberRequest.UpdateFcmToken) {
        service.updateFcmToken(memberId, request.token)
    }

    @GetMapping("/@{username}")
    fun getMember(@PathVariable username: String): MemberResponse.Public {
        val member = repo.findByUsername(username) ?: throw LanglezException(404, "member.not-found")
        return MemberResponse.Public(member)
    }

    @GetMapping("/{username}/online-status")
    fun getOnlineStatus(@PathVariable username: String): MemberResponse.OnlineStatus {
        val online = service.isOnline(username)
        return MemberResponse.OnlineStatus(online)
    }

}
