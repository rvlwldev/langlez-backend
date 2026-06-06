package com.langlez.member.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.domain.AfterDomainEventPublication
import org.springframework.data.domain.DomainEvents
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Duration
import java.time.Instant

@Entity
@Table(
    name = "MEMBER",
    indexes = [Index("IDX_MEMBER_NICKNAME", "nickname")],
    uniqueConstraints = [
        UniqueConstraint("UNQ_MEMBER_EMAIL", ["email"]),
        UniqueConstraint("UNQ_MEMBER_PROVIDER", ["provider", "provider_id"]),
        UniqueConstraint("UNQ_MEMBER_USERNAME", ["username"])
    ]
)
@EntityListeners(AuditingEntityListener::class)
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val email: String,

    @Enumerated(EnumType.STRING)
    var provider: Provider,
    val providerId: String,
    val providerDisplayName: String,

    @Column(length = 20)
    var username: String = generateRandomUsername(), // 트위터의 handle과 같은 의미
    var nickname: String,

    var isVerified: Boolean = false, // Provider 인증 이후 프로필 작성 후 True

    @Enumerated(EnumType.STRING)
    var role: Role = Role.MEMBER,
    var premiumExpiresAt: Instant? = null,

    var lastAccessAt: Instant? = null,
    var lastUsernameUpdatedAt: Instant? = null,
    var lastNicknameUpdatedAt: Instant? = null,

    @CreatedDate var createdAt: Instant = Instant.now(),
    @Version var version: Long = 0
) {
    fun access() {
        lastAccessAt = Instant.now()
    }

    fun changeUsername(newUsername: String) {}
    fun changeNickname(newNickname: String) {}
    fun toPremium(months: Int = 1) {}
    fun toMember() {}

    @Transient
    private val events = mutableListOf<Any>()

    @DomainEvents
    private fun events(): List<Any> = events.toList()

    @AfterDomainEventPublication
    private fun clearEvents() = events.clear()

    @PostPersist
    private fun onCreated() = events.add(MemberEvent.Created(id, email, username, nickname))

    enum class Provider { APPLE, GOOGLE }
    enum class Role { MEMBER, PREMIUM, ADMIN }

    companion object {
        private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,20}$")
        private val CHANGE_COOLDOWN: Duration = Duration.ofDays(15)

        fun generateRandomUsername(): String = (1..20)
            .map { "_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".random() }
            .joinToString("")

        fun isValidUsername(username: String): Boolean = USERNAME_PATTERN.matches(username)
    }
}