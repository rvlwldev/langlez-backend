package com.langlez.profile.api

import com.langlez.exception.LanglezException
import com.langlez.member.domain.MemberRepository
import com.langlez.profile.application.ProfileService
import com.langlez.security.web.MemberID
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(
    private val service: ProfileService,
    private val memberRepo: MemberRepository,
) {

    @GetMapping("/@{username}")
    fun getProfile(@MemberID visitorId: Long, @PathVariable username: String): ProfileResponse.Detail {
        CompletableFuture.runAsync { service.increaseVisitCount(visitorId, username) }

        val profileF = CompletableFuture.supplyAsync { service.getProfile(username) }
        val visitCountF = CompletableFuture.supplyAsync { service.getVisitCount(username) }
        val memberF = CompletableFuture.supplyAsync {
            memberRepo.findByUsername(username) ?: throw LanglezException(NOT_FOUND, "member.not-found")
        }

        return try {
            val profile = profileF.get()
            val member = memberF.get()
            val delta = visitCountF.get()
            ProfileResponse.Detail(profile, member, profile.visitCount + delta)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
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
        @RequestBody body: ProfileRequest.ImageConfirm,
    ): ProfileResponse.Image {
        val image = service.confirmRepresentImage(memberId, body.fileUrl)
        return ProfileResponse.Image(image)
    }

    @PostMapping("/images")
    @ResponseStatus(CREATED)
    fun confirmAdditionalImage(
        @MemberID memberId: Long,
        @RequestBody body: ProfileRequest.ImageConfirm,
    ): ProfileResponse.Image {
        val image = service.confirmAdditionalImage(memberId, body.fileUrl)
        return ProfileResponse.Image(image)
    }

    @PatchMapping("/images/represent")
    fun changeRepresentImage(
        @MemberID memberId: Long,
        @RequestBody body: ProfileRequest.ImageConfirm,
    ): ProfileResponse.Image {
        val image = service.changeRepresentImage(memberId, body.fileUrl)
        return ProfileResponse.Image(image)
    }
}
