package com.langlez.member.domain

import com.langlez.member.application.MemberEvent
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
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

    @Column(length = 20)
    var username: String = generateRandomUsername(),
    var nickname: String,

    var lastUsernameUpdatedAt: Instant? = null,
    var lastNicknameUpdatedAt: Instant? = null,

    @Enumerated(EnumType.STRING) var role: Role = Role.MEMBER,

    @Enumerated(EnumType.ORDINAL) @Column(name = "provider_type") var provider: Provider,
    @Column(name = "provider_id") var providerId: String,
    @Column(name = "provider_username") var providerDisplayName: String? = null,

    var isVerified: Boolean = false,
    var premiumExpiresAt: Instant? = null,

    @CreatedDate var createdAt: Instant = Instant.now(),
    @LastModifiedDate var updatedAt: Instant = Instant.now(),
    var deletedAt: Instant? = null,
    @Column(name = "last_logged_in_at") var lastAccessedAt: Instant? = null,

    @Version var version: Long = 0
) {
    constructor(email: String, username: String?, nickname: String, provider: Provider, providerId: String, providerDisplayName: String?) : this(
        email = email,
        username = username ?: generateRandomUsername(),
        nickname = nickname,
        provider = provider,
        providerId = providerId,
        providerDisplayName = providerDisplayName,
    )

    @Transient
    private val domainEvents: MutableList<Any> = mutableListOf()

    @org.springframework.data.domain.DomainEvents
    fun domainEvents(): List<Any> = domainEvents.toList()

    @org.springframework.data.domain.AfterDomainEventPublication
    fun clearDomainEvents() { domainEvents.clear() }

    @PostPersist
    private fun onCreated() {
        domainEvents.add(MemberEvent.Created(id, email, username, nickname))
    }

    fun login() {
        lastAccessedAt = Instant.now()
    }

    fun canChangeUsername(now: Instant = Instant.now()): Boolean =
        lastUsernameUpdatedAt == null || Duration.between(lastUsernameUpdatedAt, now) >= CHANGE_COOLDOWN

    fun canChangeNickname(now: Instant = Instant.now()): Boolean =
        lastNicknameUpdatedAt == null || Duration.between(lastNicknameUpdatedAt, now) >= CHANGE_COOLDOWN

    fun changeUsername(newUsername: String, now: Instant = Instant.now()) {
        username = newUsername
        lastUsernameUpdatedAt = now
        domainEvents.add(MemberEvent.UsernameChanged(id, newUsername))
    }

    fun changeNickname(newNickname: String, now: Instant = Instant.now()) {
        nickname = newNickname
        lastNicknameUpdatedAt = now
        domainEvents.add(MemberEvent.NicknameChanged(id, newNickname))
    }

    fun upgradeToPremium() {
        role = Role.PREMIUM
    }

    fun delete() {
        deletedAt = Instant.now()
    }

    enum class Role { MEMBER, PREMIUM, ADMIN }

    enum class Provider { GOOGLE, APPLE }

    companion object {
        private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,20}$")
        private val CHANGE_COOLDOWN: Duration = Duration.ofDays(15)

        fun generateRandomUsername(): String = (1..20)
            .map { "_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".random() }
            .joinToString("")

        fun isValidUsername(username: String): Boolean = USERNAME_PATTERN.matches(username)
    }
}


