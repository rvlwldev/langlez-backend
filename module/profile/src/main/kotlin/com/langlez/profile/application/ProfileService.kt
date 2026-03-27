package com.langlez.profile.application

import com.langlez.core.FileStorage
import com.langlez.exception.LanglezException
import com.langlez.member.domain.MemberRepository
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CompletableFuture

@Service
class ProfileService(
    private val repo: ProfileRepository,
    private val memberRepo: MemberRepository,
    private val storage: FileStorage,
    private val transaction: TransactionTemplate
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

    fun generateImageUploadUrl(filename: String, contentType: String): String {
        if (!contentType.startsWith("image/"))
            throw LanglezException(BAD_REQUEST, "file.unsupported-content-type")
        return storage.generateUploadUrl(filename, contentType, IMAGE_DIRECTORY)
    }

    fun confirmRepresentImage(memberId: Long, fileUrl: String): ProfileImage {
        return transaction.execute { replaceRepresentImage(memberId, fileUrl) }
            ?: throw LanglezException()
    }

    fun confirmAdditionalImage(memberId: Long, fileUrl: String): ProfileImage {
        if (repo.countImages(memberId) >= MAX_IMAGES)
            throw LanglezException(BAD_REQUEST, "profile.image.limit-exceeded")

        return transaction.execute {
            val sequence = repo.countImages(memberId) + 1
            repo.saveImage(ProfileImage(memberId, fileUrl, sequence, 0L, false))
        } ?: throw LanglezException()
    }

    fun changeRepresentImage(memberId: Long, fileUrl: String): ProfileImage {
        repo.findImageByUrl(memberId, fileUrl)
            ?: throw LanglezException(NOT_FOUND, "profile.image.not-found")

        return transaction.execute { replaceRepresentImage(memberId, fileUrl) }
            ?: throw LanglezException()
    }

    private fun replaceRepresentImage(memberId: Long, newUrl: String): ProfileImage {
        repo.findRepresentImage(memberId)?.apply {
            this.represent = false
            repo.saveImage(this)
        }
        val sequence = repo.countImages(memberId) + 1
        return repo.saveImage(ProfileImage(memberId, newUrl, sequence, 0L, true))
    }

    companion object {
        private const val IMAGE_DIRECTORY = "profiles"
        private const val MAX_IMAGES = 6L
    }
}
