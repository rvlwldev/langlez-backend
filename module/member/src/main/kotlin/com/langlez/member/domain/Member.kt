package com.langlez.member.domain

import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.FetchType.LAZY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "members",
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
    // handle 과 달리 유니크가 아니다 - 표시용 이름일 뿐 식별자가 아니다. nullable - 기존 회원은
    // 정하지 않았으므로 백필하지 않는다(handle 을 복사해 넣으면 사용자가 안 고른 값이 이름으로 굳는다).
    @Column(length = NICKNAME_MAX_LENGTH) var nickname: String? = null,
    @Enumerated(STRING) var status: Status = Status.CREATED,
    @Enumerated(STRING) var role: Role = Role.MEMBER,

    var country: String? = null,
    var imageUrl: String? = null,
    var agreedMarketingReceive: Boolean = false,

    // 개인식별 정보는 프로필(자기소개)이 아니라 계정에 둔다. 프로필 없이도 존재해야 한다.
    // gender 는 non-null 이므로 기존 행 백필 없이 배포하면 NULL 을 읽어 NPE 가 난다. 마이그레이션 필수.
    @Enumerated(STRING) @Column(nullable = false) var gender: Gender = Gender.SECRET,
    var birthDay: LocalDate? = null,

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

    // 백킹 필드가 없는 파생 프로퍼티라 @field: 는 컴파일이 안 된다. getter 를 막아야 한다.
    @get:Transient
    var locale: Locale?
        get() = country?.let { Locale.of("", it) }
        set(value) {
            country = value?.country
        }

    /** 가입 인증 완료. CREATED 로 머물던 계정을 여기서 ACTIVE 로 올린다. */
    fun verify() {
        require(audit.verifiedAt == null) { "member.already-verified" }
        audit.verifiedAt = Instant.now()
        audit.agreedTermsAt = Instant.now()
        if (status == Status.CREATED) status = Status.ACTIVE
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

    /** 12개 언어 앱이라 문자 종류를 제한하지 않는다. 공백만 있는 입력과 앞뒤 공백만 막는다. */
    fun changeNickname(newNickname: String) {
        val trimmed = newNickname.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= NICKNAME_MAX_LENGTH) { "member.nickname.invalid" }

        nickname = trimmed
    }

    /** 정지/탈퇴 회원은 서비스를 계속 쓸 수 없다. 로그인·토큰 갱신 경로에서 호출한다. */
    fun requireActive() {
        require(status != Status.SUSPENDED) { "member.suspended" }
        require(status != Status.WITHDRAWN) { "member.withdrawn" }
    }

    fun suspend(now: Instant = Instant.now()) {
        require(status != Status.WITHDRAWN) { "member.already-withdrawn" }
        status = Status.SUSPENDED
        audit.suspendedAt = now
    }

    /** 어드민이 정지를 푼다. 정지는 되돌릴 수 있는 상태다. */
    fun unsuspend() {
        require(status != Status.WITHDRAWN) { "member.already-withdrawn" }
        require(status == Status.SUSPENDED) { "member.not-suspended" }
        status = Status.ACTIVE
        audit.suspendedAt = null
    }

    /**
     * 탈퇴. 되돌릴 수 없다.
     *
     * 개인정보를 지우거나 익명화하지 않는다. 탈퇴 후 재가입해 같은 문제를 반복하는 회원을
     * 추적해야 해서 계정 기록을 영구 보존한다. 삭제/익명화 배치를 두지 않는 것이 의도된 정책이다.
     */
    fun withdraw(now: Instant = Instant.now()) {
        status = Status.WITHDRAWN
        audit.withdrawnAt = now
    }

    enum class Status { CREATED, ACTIVE, SUSPENDED, WITHDRAWN }

    enum class Gender { MALE, FEMALE, SECRET }

    enum class Role {
        MEMBER, PREMIUM, ADMIN;

        /** Spring Security 권한 문자열. JWT role 클레임도 이 값으로 통일한다. */
        val authority: String get() = "ROLE_$name"
    }

    enum class Provider { GOOGLE, APPLE }

    companion object {
        const val HANDLE_REGEX = "^[a-zA-Z0-9_.]{3,20}$"
        private val HANDLE_PATTERN = Regex(HANDLE_REGEX)
        private val CHANGE_COOLDOWN: Duration = Duration.ofDays(15)

        // handle(20자, 라틴 문자 전용)과 별개 상수다. CJK 는 글자당 정보량이 많아 20자면 대부분의
        // 실명·애칭이 들어가고, 리스트·채팅 헤더 같은 한 줄 UI 에서도 자연스럽게 잘리는 폭이다.
        const val NICKNAME_MAX_LENGTH = 20

        fun randomHandle(): String = (1..20)
            .map { "._ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".random() }
            .joinToString("")

        fun isValidHandle(handle: String): Boolean = HANDLE_PATTERN.matches(handle)
    }
}
