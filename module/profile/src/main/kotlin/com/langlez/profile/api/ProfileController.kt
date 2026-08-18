package com.langlez.profile.api

import com.langlez.core.Storage
import com.langlez.profile.application.ProfileService
import com.langlez.annotation.MemberId
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.*
import java.util.Locale

@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(private val service: ProfileService) {

    @GetMapping("/{username}")
    fun getProfile(@MemberId visitorId: Long, @PathVariable username: String, locale: Locale): ProfileResponse.Detail =
        service.getProfileDetail(visitorId, username, locale)

    @PatchMapping("/me")
    fun updateProfile(
        @MemberId memberId: Long,
        @RequestBody request: ProfileRequest.Update,
        locale: Locale,
    ): ProfileResponse.ProfileDetail =
        service.updateProfile(memberId, request, locale)

    @GetMapping("/images/upload-url")
    fun getImageUploadUrl(
        @MemberId memberId: Long,
        @RequestParam filename: String,
        @RequestParam contentType: String,
    ): Storage.PresignedResult =
        service.generateImageUploadUrl(memberId, filename, contentType)

    @PostMapping("/images/represent")
    @ResponseStatus(CREATED)
    fun confirmRepresentImage(
        @MemberId memberId: Long,
        @RequestBody body: ProfileRequest.ImageConfirm,
    ): ProfileResponse.Image =
        ProfileResponse.Image(service.confirmRepresentImage(memberId, body.key))

    @PostMapping("/images")
    @ResponseStatus(CREATED)
    fun confirmAdditionalImage(
        @MemberId memberId: Long,
        @RequestBody body: ProfileRequest.ImageConfirm,
    ): ProfileResponse.Image =
        ProfileResponse.Image(service.confirmAdditionalImage(memberId, body.key))

    @PatchMapping("/images/represent")
    fun changeRepresentImage(
        @MemberId memberId: Long,
        @RequestBody body: ProfileRequest.ImageSelect,
    ): ProfileResponse.Image =
        ProfileResponse.Image(service.changeRepresentImage(memberId, body.url))

    @DeleteMapping("/images")
    @ResponseStatus(NO_CONTENT)
    fun deleteImage(
        @MemberId memberId: Long,
        @RequestParam url: String,
    ) = service.deleteImage(memberId, url)
}
