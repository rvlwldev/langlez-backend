package com.langlez.profile.application

import com.langlez.core.FileStorage
import com.langlez.exception.LanglezException
import com.langlez.member.domain.MemberRepository
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture

@Service
class ProfileService(
    private val repo: ProfileRepository,
    private val memberRepo: MemberRepository,
    private val storage: FileStorage,
) {

    @Transactional(readOnly = true)
    fun getProfile(visitorId: Long, username: String): ProfileResponse {
        val member = memberRepo.findByUsername(username)
            ?: throw LanglezException(NOT_FOUND, "member.not-found")

        val profile = repo.findProfile(member.id)
            ?: throw LanglezException(NOT_FOUND, "profile.not-found")

        CompletableFuture.runAsync { repo.increaseVisitCount(visitorId, username) }

        val delta = repo.getVisitCountDelta(username)
        return ProfileResponse(profile, member, profile.visitCount + delta)
    }

    fun generateImageUploadUrl(filename: String, contentType: String): String =
        storage.generateUploadUrl(filename, contentType, IMAGE_DIRECTORY)

    @Transactional
    fun registerRepresentImage(id: Long, url: String, fileSize: Long): ProfileImage {
        val currentImage = repo.findRepresentImage(id)?.apply { this.represent = false }
        val sequence = repo.countImages(id) + 1

        if (currentImage != null) repo.saveImage(currentImage)

        return repo.saveImage(ProfileImage(id, url, sequence, fileSize, true))
    }

    companion object {
        private const val IMAGE_DIRECTORY = "profiles"
    }
}
