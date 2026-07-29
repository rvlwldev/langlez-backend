package com.langlez.profile.api

import com.langlez.profile.application.ProfileService
import com.langlez.security.web.MemberID
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.*
import java.util.Locale

@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(private val service: ProfileService) {

    @GetMapping("/{username}")
    fun getProfile(@MemberID visitorId: Long, @PathVariable username: String, locale: Locale): ProfileResponse.Detail =
        service.getProfileDetail(visitorId, username, locale)

    @PatchMapping("/me")
    fun updateProfile(
        @MemberID memberId: Long,
        @RequestBody request: ProfileRequest.Update,
        locale: Locale,
    ): ProfileResponse.ProfileDetail =
        service.updateProfile(memberId, request, locale)

    @GetMapping("/images/upload-url")
    fun getImageUploadUrl(
        @MemberID memberId: Long,
        @RequestParam filename: String,
        @RequestParam contentType: String,
    ): Map<String, String> =
        mapOf("uploadUrl" to service.generateImageUploadUrl(filename, contentType))

    @PostMapping("/images/represent")
    @ResponseStatus(CREATED)
    fun confirmRepresentImage(
        @MemberID memberId: Long,
        @RequestBody body: ProfileRequest.ImageConfirm,
    ): ProfileResponse.Image =
        ProfileResponse.Image(service.confirmRepresentImage(memberId, body.fileUrl))

    @PostMapping("/images")
    @ResponseStatus(CREATED)
    fun confirmAdditionalImage(
        @MemberID memberId: Long,
        @RequestBody body: ProfileRequest.ImageConfirm,
    ): ProfileResponse.Image =
        ProfileResponse.Image(service.confirmAdditionalImage(memberId, body.fileUrl))

    @PatchMapping("/images/represent")
    fun changeRepresentImage(
        @MemberID memberId: Long,
        @RequestBody body: ProfileRequest.ImageConfirm,
    ): ProfileResponse.Image =
        ProfileResponse.Image(service.changeRepresentImage(memberId, body.fileUrl))

    @DeleteMapping("/images")
    @ResponseStatus(NO_CONTENT)
    fun deleteImage(
        @MemberID memberId: Long,
        @RequestParam url: String,
    ) = service.deleteImage(memberId, url)
}
