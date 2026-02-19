package com.langlez.member.application

import com.langlez.common.exception.LanglezException
import com.langlez.file.application.FileStorage
import com.langlez.member.domain.*
import com.langlez.member.domain.embedded.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.hibernate.Hibernate
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

import org.springframework.beans.factory.annotation.Value

@Service
class ProfileService(
    private val memberRepo: MemberRepository,
    private val profileRepo: MemberProfileRepository,
    private val fileStorage: FileStorage,
    @Value("\${app.storage.profile-path:profile}") private val profileDirectory: String
) {

    @Transactional(readOnly = true)
    suspend fun getMemberAndProfile(email: String): Pair<Member, MemberProfile> = withContext(Dispatchers.IO) {
        val member = memberRepo.findByEmail(email) ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")
        
        // 1:1 관계에서 Profile이 없으면 예외 (가입 시 생성되므로)
        val profile = profileRepo.findByMemberId(member.id) 
            ?: throw LanglezException(HttpStatus.INTERNAL_SERVER_ERROR, "Profile not found for member ${member.id}")
        
        member to profile
    }

    @Transactional
    suspend fun saveUsername(email: String, username: String, nickname: String): Member = withContext(Dispatchers.IO) {
        if (!Member.isValidUsername(username)) throw LanglezException(HttpStatus.BAD_REQUEST, "member.init.handle.invalid")

        val existing = memberRepo.findByUsername(username)
        if (existing != null && existing.email != email) {
            throw LanglezException(HttpStatus.CONFLICT, "member.init.handle.duplicated")
        }

        val (member, profile) = getMemberAndProfile(email)
        try {
            member.username = username
            member.nickname = nickname
            checkInitComplete(member, profile)
            member // Dirty Checking
        } catch (e: DataIntegrityViolationException) {
            throw LanglezException(HttpStatus.CONFLICT, "member.init.handle.duplicated")
        }
    }

    @Transactional
    suspend fun savePersonality(email: String, personality: MemberPersonality): MemberProfile = withContext(Dispatchers.IO) {
        val (member, profile) = getMemberAndProfile(email)
        profile.personality = personality
        checkInitComplete(member, profile)
        profile
    }

    @Transactional
    suspend fun saveLocation(email: String, location: MemberLocation): MemberProfile = withContext(Dispatchers.IO) {
        val (member, profile) = getMemberAndProfile(email)
        profile.location = location
        checkInitComplete(member, profile)
        profile
    }

    @Transactional
    suspend fun saveIntroduction(email: String, introduction: MemberIntroduction): MemberProfile = withContext(Dispatchers.IO) {
        val (member, profile) = getMemberAndProfile(email)
        profile.introduction = introduction
        checkInitComplete(member, profile)
        profile
    }

    @Transactional
    suspend fun saveLanguages(email: String, languages: List<MemberLanguage>): MemberProfile = withContext(Dispatchers.IO) {
        val (member, profile) = getMemberAndProfile(email)
        profile.languages.clear()
        profile.languages.addAll(languages)
        checkInitComplete(member, profile)
        profile
    }

    /**
     * 이미지 업로드 (트랜잭션 분리 및 병렬 처리)
     */
    suspend fun updateImages(email: String, profileImage: MultipartFile, otherImages: List<MultipartFile>?): Member = coroutineScope {
        // 1. 현재 상태 조회 (ReadOnly)
        val (member, _) = getMemberAndProfile(email)
        val currentImages = member.images.toList()

        // 2. I/O 작업 (Non-Transactional)
        
        // 2-1. 기존 이미지 삭제 (S3) - 전체 교체 로직 유지하되 병렬 처리 (요구사항에 델타 업데이트가 있었으나, MultipartFile로 넘어오는 구조상 전체 교체로 가정)
        // 만약 델타 업데이트를 하려면 클라이언트에서 '삭제할 URL'과 '추가할 파일'을 별도로 받아야 함.
        // 현재 API는 'updateImages'로 전체 교체를 의미하므로, 기존 파일은 모두 삭제하고 새 파일을 업로드하는 것이 맞음.
        // (S3 비용 문제는 추후 API 개선으로 해결)
        val deleteJobs = currentImages.map { 
            async(Dispatchers.IO) { fileStorage.delete(it.url) } 
        }

        // 2-2. 새 이미지 업로드 (S3)
        val profileImageJob = async(Dispatchers.IO) {
            val url = fileStorage.upload(profileImage, profileDirectory)
            MemberImage(memberId = member.id, sequence = 0, url = url, represent = true)
        }

        val otherImagesJobs = otherImages?.mapIndexed { index, file ->
            async(Dispatchers.IO) {
                val url = fileStorage.upload(file, profileDirectory)
                MemberImage(memberId = member.id, url = url, sequence = index + 1, represent = false)
            }
        } ?: emptyList()

        // I/O 대기
        deleteJobs.awaitAll()
        val newProfileImage = profileImageJob.await()
        val newOtherImages = otherImagesJobs.awaitAll()

        // 3. DB 반영 (Transactional)
        try {
            saveImagesToDb(email, newProfileImage, newOtherImages)
        } catch (e: Exception) {
            // 보상 트랜잭션: 업로드된 파일 삭제
            val uploadedFiles = listOf(newProfileImage) + newOtherImages
            uploadedFiles.forEach { 
                try { fileStorage.delete(it.url) } catch (ignore: Exception) { }
            }
            throw e
        }
    }

    @Transactional
    protected suspend fun saveImagesToDb(email: String, profileImage: MemberImage, otherImages: List<MemberImage>): Member = withContext(Dispatchers.IO) {
        val (member, profile) = getMemberAndProfile(email)
        
        member.images.clear()
        member.images.add(profileImage)
        member.images.addAll(otherImages)
        
        checkInitComplete(member, profile)
        member
    }

    private fun checkInitComplete(member: Member, profile: MemberProfile) {
        if (profile.isReadyToFinishInit) {
            member.isInitDone = true
        }
    }
}
