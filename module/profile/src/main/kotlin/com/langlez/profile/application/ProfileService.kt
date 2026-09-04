package com.langlez.profile.application

import com.langlez.attachment.contract.Storage
import com.langlez.exception.LanglezException
import com.langlez.follow.contract.FollowReader
import com.langlez.lang.contract.LanguageReader
import com.langlez.member.contract.MemberReader
import com.langlez.profile.api.ProfileRequest
import com.langlez.profile.api.ProfileResponse
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import java.time.Instant
import java.util.Locale
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class ProfileService(
    private val repo: ProfileRepository,
    private val storage: Storage,
    private val profileImageLocker: ProfileImageLocker,
    private val follows: FollowReader,
    private val members: MemberReader,
    private val langs: LanguageReader,
    private val tx: TransactionTemplate,
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

    fun getProfile(username: String): Profile =
        repo.findProfile(memberIdOrThrow(username)) ?: throw LanglezException(404, "profile.not-found")

    /**
     * 프로필 상세.
     *
     * **한 스냅샷으로 읽지 않는다.** profile 행은 이 모듈의 DB 지만 `members`·`follows` 는
     * `*-api` 포트라 곧 gRPC/HTTP 로 나간다. 트랜잭션으로 감싸 봐야 원격 쪽 시점은 묶이지 않고,
     * 커넥션만 네트워크 대기 동안 붙잡힌다. 그래서 셋을 각각 읽고, 사이에 상대가 팔로우 수를
     * 바꾸거나 handle 을 바꾸면 화면에 섞인 시점이 실릴 수 있는 것을 감수한다 — 표시용 숫자다.
     * 여기에 `@Transactional` 을 다시 붙이지 마라.
     */
    fun getProfileDetail(visitorId: Long, username: String, locale: Locale): ProfileResponse.Detail {
        // 조회가 성공한 뒤에 센다. 앞에 두면 존재하지 않는 username 요청마다
        // HLL 키와 dirty 셋 항목이 생기고, 플러시 대상이 아니라 영영 안 지워진다.
        // 프로필 id 는 회원 id 와 같다.
        val memberId = memberIdOrThrow(username)
        val profile = repo.findProfile(memberId)
            ?: throw LanglezException(404, "profile.not-found")
        // 조회 직후 계정이 지워지는 경우만 null 이다.
        val member = members.findProfileInfo(memberId)
            ?: throw LanglezException(404, "profile.not-found")
        increaseVisitCount(visitorId, username)
        val visitDelta = getVisitCount(username)
        // 팔로워/팔로잉 수는 follow 소유라 포트로 물어본다. 프로필 화면이 두 숫자를 함께 그려서
        // 여기 실어 보낸다 — 클라이언트가 follow 엔드포인트를 따로 부르면 화면 하나에 요청이 셋이 된다.
        val counts = follows.counts(member.id)
        // 언어 프로필은 lang 소유다. 따로 엔드포인트를 두지 않고 여기 실어 보낸다 —
        // 프로필 화면이 "무슨 언어를 하고 무엇을 배우는지"를 항상 함께 그린다.
        val languages = langs.languagesOf(member.id)
        return ProfileResponse.Detail(
            profile = profile,
            member = member,
            visitCount = profile.visitCount + visitDelta,
            follows = counts,
            languages = languages,
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
     *
     * 그 확인은 S3 왕복이라 트랜잭션 밖에서 먼저 끝낸다. 인자 자리에 두면 `@Transactional` 이
     * 열린 뒤에 평가돼 DB 커넥션을 쥔 채 S3 를 기다린다.
     */
    fun confirmRepresentImage(memberId: Long, key: String): ProfileImage {
        val url = storage.attach(key, memberId)

        return tx.execute { replaceRepresentImage(memberId, url) }!!
    }

    fun confirmAdditionalImage(memberId: Long, key: String): ProfileImage {
        // 지역변수로 뽑아 attach 가 락 진입보다 먼저 끝난다는 순서를 코드에 드러낸다.
        val url = storage.attach(key, memberId)

        return profileImageLocker.confirmAdditionalImage(memberId, url)
    }

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

    fun updateProfile(memberId: Long, request: ProfileRequest.Update, locale: Locale): ProfileResponse.ProfileDetail {
        val member = members.findProfileInfo(memberId)
            ?: throw LanglezException(404, "member.not-found")

        // 읽기+쓰기는 한 트랜잭션에 묶는다. 나뉘면 그 사이 다른 수정이 끼어들어 @Version 경합이 난다.
        val saved = tx.execute {
            val profile = repo.findProfile(memberId)
                ?: throw LanglezException(404, "profile.not-found")

            request.bio?.let { profile.bio = it }
            request.goal?.let { profile.goal = it }
            request.want?.let { profile.want = it }
            request.mbti?.let { profile.mbti = it }

            repo.saveProfile(profile)
        }!!

        return ProfileResponse.ProfileDetail(saved, member)
    }

    /** handle → 회원 id 변환은 member 소유라 포트로 묻는다. 트랜잭션 밖에서만 부른다. */
    private fun memberIdOrThrow(username: String): Long =
        members.findIdByHandle(username) ?: throw LanglezException(404, "profile.not-found")

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
