package com.langlez.profile.infrastructure.jpa

import com.langlez.profile.domain.ProfileImage
import org.springframework.data.jpa.repository.JpaRepository

interface ProfileImageJpaRepository : JpaRepository<ProfileImage, ProfileImage.Key> {
    fun findByIdAndRepresentTrue(id: Long): ProfileImage?
    fun findByIdAndUrl(id: Long, url: String): ProfileImage?
    fun countById(id: Long): Long
}
