package com.langlez.profile.api

import com.langlez.profile.application.ProfileResponse
import com.langlez.profile.application.ProfileService
import com.langlez.security.web.MemberID
import org.springframework.http.HttpStatus.CREATED
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(private val service: ProfileService) {

    @GetMapping("/@{username}")
    fun getProfile(@MemberID visitorId: Long, @PathVariable username: String): ProfileResponse {
        return service.getProfile(visitorId, username)
    }

    @GetMapping("/images/upload-url")
    fun getImageUploadUrl(
        @MemberID memberId: Long,
        @RequestParam filename: String,
        @RequestParam contentType: String,
    ): Map<String, String> {
        val url = service.generateImageUploadUrl(filename, contentType)
        return mapOf("uploadUrl" to url)
    }

    @PostMapping("/images/represent")
    @ResponseStatus(CREATED)
    fun confirmRepresentImage(
        @MemberID memberId: Long,
        @RequestBody body: ImageConfirmRequest,
    ): ImageResponse {
        val image = service.confirmRepresentImage(memberId, body.fileUrl)
        return ImageResponse(image)
    }

    @PostMapping("/images")
    @ResponseStatus(CREATED)
    fun confirmAdditionalImage(
        @MemberID memberId: Long,
        @RequestBody body: ImageConfirmRequest,
    ): ImageResponse {
        val image = service.confirmAdditionalImage(memberId, body.fileUrl)
        return ImageResponse(image)
    }

    @PatchMapping("/images/represent")
    fun changeRepresentImage(
        @MemberID memberId: Long,
        @RequestBody body: ImageConfirmRequest,
    ): ImageResponse {
        val image = service.changeRepresentImage(memberId, body.fileUrl)
        return ImageResponse(image)
    }
}
