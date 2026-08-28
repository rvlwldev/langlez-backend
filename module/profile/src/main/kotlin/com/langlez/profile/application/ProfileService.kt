package com.langlez.profile.application

import com.langlez.core.FollowQuery
import com.langlez.core.MemberQuery
import com.langlez.core.Storage
import com.langlez.exception.LanglezException
import com.langlez.profile.api.ProfileRequest
import com.langlez.profile.api.ProfileResponse
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Locale

@Service
class ProfileService(
    private val repo: ProfileRepository,
    private val storage: Storage,
    private val profileImageLocker: ProfileImageLocker,
    private val follows: FollowQuery,
    private val members: MemberQuery,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 가입 이벤트를 받아 프로필 행을 만든다. 프로필 id 는 회원 id 와 같다.
     *
     * 카프카는 at-least-once 라 같은 이벤트가 다시 온다. 이미 있으면 아무것도 하지 않는다.
     * 존재 확인과 INSERT 사이에 백필 마이그레이션이나 다른 인스턴스가 끼어들 수 있어
     * PK 충돌도 함께 삼킨다 — 여기서 던지면 예외가 리스너로 올라가 파티션이 막힌다.
     *
     * 일부러 `@Transactional` 을 걸지 않았다. 트랜잭션을 열면 INSERT 가 커밋 시점까지 미뤄져
     * 제약 위반이 이 try/catch 밖에서 터진다. `repo.saveProfile` 이 자기 트랜잭션을 가지므로
     * 그 경계에서 예외가 올라와야 여기서 잡을 수 있다.
     */
    fun createProfileIfAbsent(memberId: Long) {
        if (repo.findProfile(memberId) != null) return

        try {
            repo.saveProfile(Profile(id = memberId))
        } catch (e: DataIntegrityViolationException) {
            log.debug("프로필이 이미 있다. memberId={}", memberId, e)
        }
    }

    @Transactional(readOnly = true)
    fun getProfile(username: String): Profile =
        repo.findProfileByUsername(username) ?: throw LanglezException(404, "profile.not-found")

    @Transactional
    fun getProfileDetail(visitorId: Long, username: String, locale: Locale): ProfileResponse.Detail {
        // 조회가 성공한 뒤에 센다. 앞에 두면 존재하지 않는 username 요청마다
        // HLL 키와 dirty 셋 항목이 생기고, 플러시 대상이 아니라 영영 안 지워진다.
        val profile = repo.findProfileByUsername(username)
            ?: throw LanglezException(404, "profile.not-found")
        // 프로필 id 는 회원 id 와 같다. 조회 직후 계정이 지워지는 경우만 null 이다.
        val member = members.findProfileInfo(profile.id)
            ?: throw LanglezException(404, "profile.not-found")
        increaseVisitCount(visitorId, username)
        val visitDelta = getVisitCount(username)
        // 팔로워/팔로잉 수는 relationship 소유라 core 포트로 물어본다. 프로필 화면이 두 숫자를 함께 그려서
        // 여기 실어 보낸다 — 클라이언트가 relationship 엔드포인트를 따로 부르면 화면 하나에 요청이 셋이 된다.
        return ProfileResponse.Detail(
            profile,
            member,
            profile.visitCount + visitDelta,
            follows.counts(member.id),
        )
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
        val member = members.findProfileInfo(memberId)
            ?: throw LanglezException(404, "member.not-found")

        request.bio?.let { profile.bio = it }
        request.goal?.let { profile.goal = it }
        request.want?.let { profile.want = it }
        request.mbti?.let { profile.mbti = it }
        request.languageLevel?.let { profile.languageLevel = it }

        val saved = repo.saveProfile(profile)
        return ProfileResponse.ProfileDetail(saved, member)
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
