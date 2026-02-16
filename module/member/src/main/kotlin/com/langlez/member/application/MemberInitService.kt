package com.langlez.member.application

import com.langlez.common.exception.LanglezException
import com.langlez.file.application.FileStorage
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberImage
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.embedded.MemberIntroduction
import com.langlez.member.domain.embedded.MemberLanguage
import com.langlez.member.domain.embedded.MemberLocation
import com.langlez.member.domain.embedded.MemberPersonality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

/**
 * Member 초기화 전용 서비스
 * - 가입 단계별 정보 입력 (handle & nickname, personality, location, introduction, languages, images)
 * - 초기화 완료 처리
 */
@Service
@Transactional
class MemberInitService(private val repo: MemberRepository, private val storage: FileStorage) {

    /**
     * Handle과 닉네임 초기화
     * - handle은 트위터 @username 같은 공개 식별자
     */
    fun initHandle(email: String, handle: String, nickname: String): Member {
        if (!Member.isValidHandle(handle))
            throw LanglezException(HttpStatus.BAD_REQUEST, "member.init.handle.invalid")

        val member = findByEmailOrThrow(email)
        if (member.init) throw LanglezException(HttpStatus.BAD_REQUEST, "error.bad-request")

        // 중복 체크
        if (repo.existsByHandle(handle))
            throw LanglezException(HttpStatus.CONFLICT, "member.init.handle.duplicated")

        try {
            member.handle = handle
            member.nickname = nickname
            return member
        } catch (e: DataIntegrityViolationException) {
            throw LanglezException(HttpStatus.CONFLICT, "member.init.handle.duplicated")
        }
    }

    fun initPersonality(email: String, personality: MemberPersonality): Member =
        findByEmailOrThrow(email).apply { this.personality = personality }

    fun initLocation(email: String, location: MemberLocation): Member =
        findByEmailOrThrow(email).apply { this.location = location }

    fun initIntroduction(email: String, introduction: MemberIntroduction): Member =
        findByEmailOrThrow(email).apply { this.introduction = introduction }

    fun initLanguages(email: String, languages: List<MemberLanguage>): Member =
        findByEmailOrThrow(email).apply {
            this.languages.clear()
            this.languages.addAll(languages)
        }

    fun initProfileImages(email: String, profileImage: MultipartFile, otherImages: List<MultipartFile>?): Member {
        val member = findByEmailOrThrow(email)
        val directory = "profile"

        // 기존 이미지 삭제
        member.images.forEach { storage.delete(it.url) }
        member.images.clear()

        runBlocking(Dispatchers.IO) {
            // 1. 대표 이미지 업로드 (비동기)
            val profileImageAsync = async {
                val url = storage.upload(profileImage, directory)
                MemberImage(memberId = member.id, sequence = 0, url = url, represent = true)
            }

            // 2. 나머지 이미지 업로드 (병렬 처리)
            val otherImagesAsync = otherImages?.mapIndexed { index, file ->
                async {
                    val url = storage.upload(file, directory)
                    MemberImage(memberId = member.id, url = url, sequence = index + 1, represent = false)
                }
            }

            // 3. 결과 조합 및 저장
            member.images.add(profileImageAsync.await())
            otherImagesAsync?.awaitAll()?.let { member.images.addAll(it) }
        }

        return member
    }

    /**
     * 초기화 완료 처리
     * - 필수 정보(handle, personality, location, introduction, languages, images)가 모두 입력되었는지 검증
     */
    fun finishInit(email: String): Member {
        val member = findByEmailOrThrow(email)

        if (!member.isReadyToFinishInit)
            throw LanglezException(HttpStatus.BAD_REQUEST, "member.init.incomplete")
        member.init = true

        return member
    }

    private fun findByEmailOrThrow(email: String): Member =
        repo.findByEmail(email) ?: throw LanglezException(HttpStatus.NOT_FOUND, "error.bad-request")

}
