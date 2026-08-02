package com.langlez.member.domain

import jakarta.persistence.*
import jakarta.persistence.EnumType.STRING
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Duration
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
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

    @Column(length = 20) var username: String = generateRandomUsername(),
    @Column(length = 20) var nickname: String,
    @Enumerated(STRING) var status: MemberStatus = MemberStatus.CREATED,
    @Enumerated(STRING) var role: MemberRole = MemberRole.MEMBER,

    var imageUrl: String? = null,
    var agreedMarketingReceive: Boolean = false,

    @Enumerated(STRING) @Column(name = "provider_type") var provider: MemberProvider,
    @Column(name = "provider_id") var providerId: String,
    @Column(name = "provider_display_name") var providerDisplayName: String? = null,

    var fcm: String? = null,

    var lastAccessedIp: String? = null,

    @CreatedDate var createdAt: Instant = Instant.now(),
    var verifiedAt: Instant? = null,
    var agreedTermsAt: Instant? = null,
    var lastUsernameUpdatedAt: Instant? = null,
    var lastNicknameUpdatedAt: Instant? = null,
    var lastAccessedAt: Instant? = null,
    var suspendedAt: Instant? = null,
    var withdrawnAt: Instant? = null,

    @Version var version: Long = 0
) {
    fun verify() {
        require(verifiedAt == null) { "member.already-verified" }
        verifiedAt = Instant.now()
        agreedTermsAt = Instant.now()
    }

    fun updateAccessedAt(accessedAt: Instant = Instant.now()) {
        val last = lastAccessedAt
        if (last == null || accessedAt > last) this.lastAccessedAt = accessedAt
    }

    fun canChangeUsername(now: Instant = Instant.now()): Boolean =
        lastUsernameUpdatedAt == null || Duration.between(lastUsernameUpdatedAt, now) >= CHANGE_COOLDOWN

    fun changeUsername(newUsername: String, now: Instant = Instant.now()) {
        require(canChangeUsername()) { "member.username.cooldown" }
        require(isValidUsername(newUsername)) { "member.username.invalid" }

        username = newUsername
        lastUsernameUpdatedAt = now
    }

    fun canChangeNickname(now: Instant = Instant.now()): Boolean =
        lastNicknameUpdatedAt == null || Duration.between(lastNicknameUpdatedAt, now) >= CHANGE_COOLDOWN

    fun changeNickname(newNickname: String, now: Instant = Instant.now()) {
        require(canChangeNickname()) { "member.nickname.cooldown" }

        nickname = newNickname
        lastNicknameUpdatedAt = now
    }

    companion object {
        const val USERNAME_REGEX = "^[a-zA-Z0-9_]{3,20}$"
        private val USERNAME_PATTERN = Regex(USERNAME_REGEX)
        private val CHANGE_COOLDOWN: Duration = Duration.ofDays(15)

        fun generateRandomUsername(): String = (1..20)
            .map { "_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".random() }
            .joinToString("")

        fun isValidUsername(username: String): Boolean = USERNAME_PATTERN.matches(username)
    }
}

