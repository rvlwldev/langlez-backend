package com.langlez.member.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import java.time.Instant

@Entity
@Table(
    name = "members",
    indexes = [Index("IDX_MEMBER_NICKNAME", "nickname")],
    uniqueConstraints = [
        UniqueConstraint("UNQ_MEMBER_PROVIDER", ["provider_id", "provider_type"]),
        UniqueConstraint("UNQ_MEMBER_EMAIL", ["email"]),
        UniqueConstraint("UNQ_MEMBER_USERNAME", ["username"])
    ]
)
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val email: String,

    @Column(length = 20)
    var username: String = generateRandomUsername(),
    var nickname: String,

    @Enumerated(EnumType.STRING) var role: Role = Role.MEMBER,
    @Embedded val provider: MemberProvider,

    var isVerified: Boolean = false,

    @CreatedDate var createdAt: Instant = Instant.now(),
    @LastModifiedDate var updatedAt: Instant = Instant.now(),
    var deletedAt: Instant? = null,
    var lastLoggedInAt: Instant? = null,

    @Version var version: Long = 0
) {
    constructor(email: String, username: String?, nickname: String, provider: MemberProvider) : this(
        email = email,
        username = username ?: generateRandomUsername(),
        nickname = nickname,
        provider = provider,
    )

    fun login() {
        lastLoggedInAt = Instant.now()
    }

    fun upgradeToPremium() {
        role = Role.PREMIUM
    }

    fun delete() {
        deletedAt = Instant.now()
    }

    enum class Role { MEMBER, PREMIUM, ADMIN }

    companion object {
        private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,20}$")

        fun generateRandomUsername(): String = (1..20)
            .map { "_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".random() }
            .joinToString("")

        fun isValidUsername(username: String): Boolean = USERNAME_PATTERN.matches(username)
    }
}


