package com.langlez.profile.application

import com.langlez.core.Storage
import com.langlez.exception.LanglezException
import com.langlez.profile.api.ProfileRequest
import com.langlez.profile.api.ProfileResponse
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Locale

@Service
class ProfileService(
    private val repo: ProfileRepository,
    private val storage: Storage,
    private val profileImageLocker: ProfileImageLocker,
) {

    @Transactional(readOnly = true)
    fun getProfile(username: String): Profile =
        repo.findProfileByUsername(username) ?: throw LanglezException(404, "profile.not-found")

    @Transactional
    fun getProfileDetail(visitorId: Long, username: String, locale: Locale): ProfileResponse.Detail {
        // 조회가 성공한 뒤에 센다. 앞에 두면 존재하지 않는 username 요청마다
        // HLL 키와 dirty 셋 항목이 생기고, 플러시 대상이 아니라 영영 안 지워진다.
        val profile = repo.findProfileByUsername(username)
            ?: throw LanglezException(404, "profile.not-found")
        increaseVisitCount(visitorId, username)
        val visitDelta = getVisitCount(username)
        return ProfileResponse.Detail(profile, profile.member, profile.visitCount + visitDelta)
    }

    fun increaseVisitCount(visitorId: Long, username: String) {
        repo.increaseVisitCount(visitorId, username)
    }

    fun getVisitCount(username: String): Long =
        repo.getVisitCountDelta(username)

    /** key 를 함께 내려줘야 클라이언트가 서명 붙은 PUT URL 대신 key 로 확정할 수 있다. */
    fun generateImageUploadUrl(memberId: Long, filename: String, contentType: String): Storage.PresignedResult {
        if (!contentType.startsWith("image/")) throw LanglezException(400, "file.unsupported-content-type")
        return storage.presign(memberId, IMAGE_DIRECTORY, Storage.Type.IMAGE, filename)
    }

    /**
     * 확정은 key 로만 받는다. 클라이언트가 준 URL 을 그대로 저장하면
     * 외부 URL 을 프로필 사진으로 심거나, 서명이 붙은 presigned URL 이 그대로 노출된다.
     * `storage.attach` 가 실제 업로드 여부를 확인하고 조회용 URL 을 돌려준다.
     */
    @Transactional
    fun confirmRepresentImage(memberId: Long, key: String): ProfileImage =
        replaceRepresentImage(memberId, storage.attach(key, memberId))

    fun confirmAdditionalImage(memberId: Long, key: String): ProfileImage =
        profileImageLocker.confirmAdditionalImage(memberId, storage.attach(key, memberId))

    @Transactional
    fun changeRepresentImage(memberId: Long, fileUrl: String): ProfileImage {
        val target = repo.findImageByUrl(memberId, fileUrl)
            ?: throw LanglezException(404, "profile.image.not-found")
        repo.findRepresentImage(memberId)?.apply {
            represent = false
            repo.saveImage(this)
        }
        target.represent = true
        return repo.saveImage(target)
    }

    @Transactional
    fun deleteImage(memberId: Long, fileUrl: String) {
        val image = repo.findImageByUrl(memberId, fileUrl)
            ?: throw LanglezException(404, "profile.image.not-found")
        image.deletedAt = Instant.now()
        repo.saveImage(image)
    }

    @Transactional
    fun updateProfile(memberId: Long, request: ProfileRequest.Update, locale: Locale): ProfileResponse.ProfileDetail {
        val profile = repo.findProfile(memberId)
            ?: throw LanglezException(404, "profile.not-found")

        request.bio?.let { profile.bio = it }
        request.goal?.let { profile.goal = it }
        request.want?.let { profile.want = it }
        request.mbti?.let { profile.mbti = it }
        request.languageLevel?.let { profile.languageLevel = it }

        // 개인식별 정보는 계정 소유라 Member 에 반영한다. Profile 이 member 를 물고 있어 같은 트랜잭션에서 함께 flush 된다.
        request.gender?.let { profile.member.gender = it }
        request.locale?.let { profile.member.locale = it }
        request.birthDay?.let { profile.member.birthDay = it }

        val saved = repo.saveProfile(profile)
        return ProfileResponse.ProfileDetail(saved)
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
