package com.langlez.member.domain

import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.FetchType.LAZY
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
    @Enumerated(STRING) var status: Status = Status.CREATED,
    @Enumerated(STRING) var role: Role = Role.MEMBER,

    var imageUrl: String? = null,
    var agreedMarketingReceive: Boolean = false,

    @Enumerated(STRING) @Column(name = "provider_type") var provider: Provider,
    @Column(name = "provider_id") var providerId: String,
    @Column(name = "provider_display_name") var providerDisplayName: String? = null,

    var fcm: String? = null,

    @OneToOne(fetch = LAZY, cascade = [ALL], orphanRemoval = true)
    @JoinColumn(name = "member_audit_id")
    val audit: MemberAudit = MemberAudit(),

    @CreatedDate var createdAt: Instant = Instant.now(),

    @Version var version: Long = 0
) {
    enum class Status { CREATED, ACTIVE, SUSPENDED, WITHDRAWN }
    enum class Role { MEMBER, PREMIUM, ADMIN }
    enum class Provider { GOOGLE, APPLE }

    fun verify() {
        require(audit.verifiedAt == null) { "member.already-verified" }
        audit.verifiedAt = Instant.now()
        audit.agreedTermsAt = Instant.now()
    }

    fun updateAccessedAt(accessedAt: Instant = Instant.now()) {
        val last = audit.lastAccessedAt
        if (last == null || accessedAt > last) audit.lastAccessedAt = accessedAt
    }

    fun canChangeUsername(now: Instant = Instant.now()): Boolean =
        audit.lastUsernameUpdatedAt == null || Duration.between(audit.lastUsernameUpdatedAt, now) >= CHANGE_COOLDOWN

    fun changeUsername(newUsername: String, now: Instant = Instant.now()) {
        require(canChangeUsername(now)) { "member.username.cooldown" }
        require(isValidUsername(newUsername)) { "member.username.invalid" }

        username = newUsername
        audit.lastUsernameUpdatedAt = now
    }

    fun canChangeNickname(now: Instant = Instant.now()): Boolean =
        audit.lastNicknameUpdatedAt == null || Duration.between(audit.lastNicknameUpdatedAt, now) >= CHANGE_COOLDOWN

    fun changeNickname(newNickname: String, now: Instant = Instant.now()) {
        require(canChangeNickname(now)) { "member.nickname.cooldown" }

        nickname = newNickname
        audit.lastNicknameUpdatedAt = now
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
