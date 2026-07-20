package com.langlez.profile.application

import com.langlez.core.LanglezException
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import com.langlez.redis.distributedLock.DistributedLock
import com.langlez.redis.distributedLock.LockKey
import org.springframework.stereotype.Component

@Component
class ProfileImageLocker(
    private val repo: ProfileRepository,
) {
    @DistributedLock(prefix = "lock:profile-image:", ttl = 5, retries = 20, wait = 100, transactional = true)
    fun confirmAdditionalImage(@LockKey memberId: Long, fileUrl: String): ProfileImage {
        if (repo.countImages(memberId) >= MAX_IMAGES) {
            throw LanglezException(400, "profile.image.limit-exceeded")
        }
        val sequence = repo.countImages(memberId) + 1
        return repo.saveImage(ProfileImage(memberId, fileUrl, sequence, 0L, false))
    }

    companion object {
        private const val MAX_IMAGES = 6L
    }
}
