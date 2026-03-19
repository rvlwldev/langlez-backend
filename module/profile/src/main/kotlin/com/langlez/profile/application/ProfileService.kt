package com.langlez.profile.application

import com.langlez.exception.LanglezException
import com.langlez.file.application.FileStorage
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import javax.imageio.ImageIO.read

// TODO : 에러메세지 설정

@Service
class ProfileService(
    private val repo: ProfileRepository,
    private val storage: FileStorage,
    private val transaction: TransactionTemplate
) {

    @Transactional
    fun createNewProfile() {
        TODO()
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