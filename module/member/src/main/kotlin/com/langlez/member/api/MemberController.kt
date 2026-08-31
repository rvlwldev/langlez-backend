package com.langlez.member.api

import com.langlez.annotation.MemberId
import com.langlez.attachment.contract.Storage
import com.langlez.exception.LanglezException
import com.langlez.member.api.request.MemberUpdateFcmTokenRequest
import com.langlez.member.api.request.MemberUpdateHandleRequest
import com.langlez.member.api.request.MemberUpdateImageRequest
import com.langlez.member.api.request.MemberUpdatePersonalInfoRequest
import com.langlez.member.api.response.MemberMeResponse
import com.langlez.member.api.response.MemberOnlineStatusResponse
import com.langlez.member.api.response.MemberPublicResponse
import com.langlez.member.application.MemberService
import com.langlez.member.domain.MemberRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/members")
class MemberController(private val service: MemberService, private val repo: MemberRepository) : MemberAPI {

    @GetMapping("/me")
    override fun getMe(@MemberId memberId: Long): MemberMeResponse {
        val member = repo.find(memberId) ?: throw LanglezException(404, "member.not-found")
        return MemberMeResponse(member)
    }

    @PatchMapping("/me/handle")
    override fun patchHandle(
        @MemberId memberId: Long,
        @RequestBody @Valid request: MemberUpdateHandleRequest
    ): MemberMeResponse {
        val member = service.updateHandle(memberId, request.handle)
        return MemberMeResponse(member)
    }

    @PatchMapping("/me")
    override fun patchPersonalInfo(
        @MemberId memberId: Long,
        @RequestBody @Valid request: MemberUpdatePersonalInfoRequest
    ): MemberMeResponse {
        val member = service.updatePersonalInfo(
            id = memberId,
            gender = request.gender,
            birthDay = request.birthDay,
            country = request.country,
            nickname = request.nickname,
        )
        return MemberMeResponse(member)
    }

    @PatchMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun patchFcmToken(@MemberId memberId: Long, @RequestBody @Valid request: MemberUpdateFcmTokenRequest) {
        service.updateFcmToken(memberId, request.token)
    }

    @GetMapping("/me/image/upload-url")
    override fun getImageUploadUrl(@MemberId memberId: Long, @RequestParam filename: String): Storage.PresignedResult =
        service.presignProfileUrl(memberId, filename)

    @PatchMapping("/me/image")
    override fun patchImage(
        @MemberId memberId: Long,
        @RequestBody @Valid request: MemberUpdateImageRequest
    ): MemberMeResponse {
        val member = service.updateProfileUrl(memberId, request.key)
        return MemberMeResponse(member)
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun withdraw(@MemberId memberId: Long) {
        service.withdrawMember(memberId)
    }

    @GetMapping("/{handle}")
    override fun getMember(@PathVariable handle: String): MemberPublicResponse {
        val member = repo.find(handle) ?: throw LanglezException(404, "member.not-found")
        return MemberPublicResponse(member)
    }

    @GetMapping("/{handle}/online-status")
    override fun getOnlineStatus(@PathVariable handle: String): MemberOnlineStatusResponse {
        val online = service.isOnline(handle)
        return MemberOnlineStatusResponse(online)
    }
}