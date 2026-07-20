package com.langlez.profile.application

import com.langlez.core.FileStorage
import com.langlez.core.LanglezException
import com.langlez.profile.api.ProfileRequest
import com.langlez.profile.api.ProfileResponse
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Service
class ProfileService(
    private val repo: ProfileRepository,
    private val storage: FileStorage,
    private val transaction: TransactionTemplate,
    private val profileImageLocker: ProfileImageLocker,
) {

    @Transactional(readOnly = true)
    fun getProfile(username: String): Profile =
        repo.findProfileByUsername(username) ?: throw LanglezException(404, "profile.not-found")

    @Transactional(readOnly = true)
    fun getProfileDetail(visitorId: Long, username: String): ProfileResponse.Detail {
        increaseVisitCount(visitorId, username)
        val profile = repo.findProfileByUsername(username)
            ?: throw LanglezException(404, "profile.not-found")
        val visitDelta = getVisitCount(username)
        return ProfileResponse.Detail(profile, profile.member, profile.visitCount + visitDelta)
    }

    fun increaseVisitCount(visitorId: Long, username: String) {
        repo.increaseVisitCount(visitorId, username)
    }

    fun getVisitCount(username: String): Long =
        repo.getVisitCountDelta(username)

    fun generateImageUploadUrl(filename: String, contentType: String): String {
        if (!contentType.startsWith("image/")) throw LanglezException(400, "file.unsupported-content-type")
        return storage.generateUploadUrl(filename, contentType, IMAGE_DIRECTORY)
    }

    fun confirmRepresentImage(memberId: Long, fileUrl: String): ProfileImage =
        transaction.execute { replaceRepresentImage(memberId, fileUrl) }
            ?: throw LanglezException(500, "profile.image.confirm-failed")

    fun confirmAdditionalImage(memberId: Long, fileUrl: String): ProfileImage =
        profileImageLocker.confirmAdditionalImage(memberId, fileUrl)

    fun changeRepresentImage(memberId: Long, fileUrl: String): ProfileImage =
        transaction.execute {
            val target = repo.findImageByUrl(memberId, fileUrl)
                ?: throw LanglezException(404, "profile.image.not-found")
            repo.findRepresentImage(memberId)?.apply {
                represent = false
                repo.saveImage(this)
            }
            target.represent = true
            repo.saveImage(target)
        } ?: throw LanglezException(500, "profile.image.change-failed")

    fun deleteImage(memberId: Long, fileUrl: String) {
        transaction.execute {
            val image = repo.findImageByUrl(memberId, fileUrl)
                ?: throw LanglezException(404, "profile.image.not-found")
            image.deletedAt = Instant.now()
            repo.saveImage(image)
        }
    }

    @Transactional
    fun updateProfile(memberId: Long, request: ProfileRequest.Update): Profile {
        val profile = repo.findProfile(memberId)
            ?: throw LanglezException(404, "profile.not-found")

        request.bio?.let { profile.bio = it }
        request.goal?.let { profile.goal = it }
        request.want?.let { profile.want = it }
        request.gender?.let { profile.gender = it }
        request.mbti?.let { profile.mbti = it }
        request.locale?.let { profile.locale = it }
        request.birthDay?.let { profile.birthDay = it }
        request.languageLevel?.let { profile.languageLevel = it }
        request.interests?.let { profile.interests = it.toMutableSet() }

        return repo.saveProfile(profile)
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
    }
}
