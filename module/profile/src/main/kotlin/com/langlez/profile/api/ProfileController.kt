package com.langlez.profile.api

import com.langlez.profile.application.ProfileResponse
import com.langlez.profile.application.ProfileService
import com.langlez.profile.domain.ProfileImage
import com.langlez.security.web.MemberID
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(private val service: ProfileService) {

    @GetMapping("/@{username}")
    fun getProfile(@MemberID visitorId: Long, @PathVariable username: String): ProfileResponse =
        service.getProfile(visitorId, username)

    @GetMapping("/me/images/upload-url")
    fun getImageUploadUrl(
        @MemberID memberId: Long,
        @RequestParam @NotBlank filename: String,
        @RequestParam @NotBlank contentType: String,
    ): Map<String, String> =
        mapOf("url" to service.generateImageUploadUrl(filename, contentType))

    @PostMapping("/me/images")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerRepresentImage(
        @MemberID memberId: Long,
        @RequestBody @Valid request: RegisterImageRequest,
    ): ProfileImage =
        service.registerRepresentImage(memberId, request.url, request.fileSize)
}

data class RegisterImageRequest(
    @field:NotBlank val url: String,
    val fileSize: Long,
)
