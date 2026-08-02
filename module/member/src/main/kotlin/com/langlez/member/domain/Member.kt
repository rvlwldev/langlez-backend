package com.langlez.member.domain

import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.FetchType.LAZY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Duration
import java.time.Instant
import java.util.Locale

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "members",
    indexes = [Index("IDX_MEMBER_NICKNAME", "nickname")],
    uniqueConstraints = [
        UniqueConstraint("UNQ_MEMBER_PROVIDER", ["provider_id", "provider_type"]),
        UniqueConstraint("UNQ_MEMBER_EMAIL", ["email"]),
        UniqueConstraint("UNQ_MEMBER_HANDLE", ["handle"]),
        UniqueConstraint("UNQ_MEMBER_AUDIT", ["member_audit_id"])
    ]
)
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val email: String,

    @Column(length = 20) var handle: String = randomHandle(),
    @Column(length = 20) var nickname: String,
    @Enumerated(STRING) var status: Status = Status.CREATED,
    @Enumerated(STRING) var role: Role = Role.MEMBER,

    var country: String? = null,
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

    @get:Transient
    var locale: Locale?
        get() = country?.let { Locale.of("", it) }
        set(value) {
            country = value?.country
        }

    fun verify() {
        require(audit.verifiedAt == null) { "member.already-verified" }
        audit.verifiedAt = Instant.now()
        audit.agreedTermsAt = Instant.now()
    }

    fun updateAccessedAt(accessedAt: Instant = Instant.now()) {
        val last = audit.lastAccessedAt
        if (last == null || accessedAt > last) audit.lastAccessedAt = accessedAt
    }

    fun canChangeHandle(now: Instant = Instant.now()): Boolean =
        audit.lastHandleUpdatedAt == null || Duration.between(audit.lastHandleUpdatedAt, now) >= CHANGE_COOLDOWN

    fun changeHandle(newHandle: String, now: Instant = Instant.now()) {
        require(canChangeHandle(now)) { "member.handle.cooldown" }
        require(isValidHandle(newHandle)) { "member.handle.invalid" }

        handle = newHandle
        audit.lastHandleUpdatedAt = now
    }

    fun canChangeNickname(now: Instant = Instant.now()): Boolean =
        audit.lastNicknameUpdatedAt == null || Duration.between(audit.lastNicknameUpdatedAt, now) >= CHANGE_COOLDOWN

    fun changeNickname(newNickname: String, now: Instant = Instant.now()) {
        require(canChangeNickname(now)) { "member.nickname.cooldown" }

        nickname = newNickname
        audit.lastNicknameUpdatedAt = now
    }

    enum class Status { CREATED, ACTIVE, SUSPENDED, WITHDRAWN }
    enum class Role { MEMBER, PREMIUM, ADMIN }
    enum class Provider { GOOGLE, APPLE }

    companion object {
        const val HANDLE_REGEX = "^[a-zA-Z0-9_.]{3,20}$"
        private val HANDLE_PATTERN = Regex(HANDLE_REGEX)
        private val CHANGE_COOLDOWN: Duration = Duration.ofDays(15)

        fun randomHandle(): String = (1..20)
            .map { "._ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".random() }
            .joinToString("")

        fun isValidHandle(handle: String): Boolean = HANDLE_PATTERN.matches(handle)
    }
}
