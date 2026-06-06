package com.langlez.profile.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.io.Serializable
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
@IdClass(ProfileImage.Key::class)
@Table(
    name = "member_image_urls",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_PROFILE_IMAGE_URL", columnNames = ["id", "url"])]
)
class ProfileImage(
    @Id val id: Long,
    @Id val url: String,
    val sequence: Long,
    val fileSize: Long,
    var represent: Boolean = false,
    @CreatedDate var createdAt: Instant = Instant.now(),
    var deletedAt: Instant? = null
) {
    data class Key(val id: Long = 0, val url: String = "") : Serializable
}