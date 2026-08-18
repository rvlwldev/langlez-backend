package com.langlez.profile.infrastructure.jpa

import com.langlez.profile.domain.ProfileImage
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 삭제는 `deletedAt` 을 채우는 soft delete 다. 조회/집계는 반드시 살아 있는 것만 세야 한다.
 * 안 그러면 지운 사진이 6장 제한에 계속 잡혀, 업로드/삭제를 6번 반복한 회원이 영구히 잠긴다.
 */
interface ProfileImageJpaRepository : JpaRepository<ProfileImage, ProfileImage.Key> {
    fun findByIdAndRepresentTrueAndDeletedAtIsNull(id: Long): ProfileImage?
    fun findByIdAndUrlAndDeletedAtIsNull(id: Long, url: String): ProfileImage?
    fun countByIdAndDeletedAtIsNull(id: Long): Long
}
