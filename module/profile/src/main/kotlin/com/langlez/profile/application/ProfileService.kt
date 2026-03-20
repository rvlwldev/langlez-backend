package com.langlez.profile.application

import com.langlez.exception.LanglezException
import com.langlez.file.application.FileStorage
import com.langlez.member.domain.MemberRepository
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO.read

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

    fun uploadNewRepresentImage(id: Long, image: MultipartFile): ProfileImage {
        validateImage(image)

        val url = storage.upload(image, IMAGE_DIRECTORY)

        return transaction.execute { replaceRepresentImageURL(id, url, image.size) }
            ?: throw LanglezException()
    }

    private fun replaceRepresentImageURL(id: Long, url: String, size: Long): ProfileImage {
        val currentImage = repo.findRepresentImage(id)?.apply { this.represent = false }
        val sequence = repo.countImages(id) + 1

        if (currentImage != null) repo.saveImage(currentImage)

        return repo.saveImage(ProfileImage(id, url, sequence, size, true))
    }

    private fun validateImage(image: MultipartFile) {
        if (image.isEmpty || image.inputStream.use { stream -> read(stream) == null })
            throw LanglezException(BAD_REQUEST)
    }

    companion object {
        private const val IMAGE_DIRECTORY = "/profiles"
    }
}
