package com.langlez.profile.application

import com.langlez.exception.LanglezException
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import com.langlez.redis.distributedLock.DistributedLock
import com.langlez.redis.distributedLock.LockKey
import org.springframework.stereotype.Component

@Component
class ProfileImageLocker(
    private val repo: ProfileRepository,
) {
    // leaseSecs 를 안 주면 Redisson watchdog 이 트랜잭션이 끝날 때까지 락을 계속 갱신한다.
    // 고정 leaseSecs 를 주면 트랜잭션이 그 시간을 넘기는 순간 락이 먼저 풀려 정원 검사가 무력화된다.
    @DistributedLock(prefix = "lock:profile-image:", retries = 20, waitMs = 100, transactional = true, throwOnFailure = true)
    fun confirmAdditionalImage(@LockKey memberId: Long, fileUrl: String): ProfileImage {
        val count = repo.countImages(memberId)
        if (count >= MAX_IMAGES) {
            throw LanglezException(400, "profile.image.limit-exceeded")
        }
        val sequence = count + 1
        return repo.saveImage(ProfileImage(memberId, fileUrl, sequence, 0L, false))
    }

    /**
     * 대표 사진도 새 행을 추가하는 것이라 정원 검사를 똑같이 거친다.
     * 이 검사가 없으면 동시성 없이 순차 호출만으로도 정원을 넘길 수 있었다.
     */
    @DistributedLock(prefix = "lock:profile-image:", retries = 20, waitMs = 100, transactional = true, throwOnFailure = true)
    fun confirmRepresentImage(@LockKey memberId: Long, fileUrl: String): ProfileImage {
        repo.findRepresentImage(memberId)?.apply {
            represent = false
            repo.saveImage(this)
        }
        val count = repo.countImages(memberId)
        if (count >= MAX_IMAGES) {
            throw LanglezException(400, "profile.image.limit-exceeded")
        }
        val sequence = count + 1
        return repo.saveImage(ProfileImage(memberId, fileUrl, sequence, 0L, true))
    }

    companion object {
        private const val MAX_IMAGES = 6L
    }
}
