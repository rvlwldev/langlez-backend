package com.langlez.member.domain

import com.langlez.member.domain.embedded.MemberAudit
import com.langlez.member.domain.embedded.MemberIntroduction
import com.langlez.member.domain.embedded.MemberLanguage
import com.langlez.member.domain.embedded.MemberLocation
import com.langlez.member.domain.embedded.MemberPersonality
import com.langlez.member.domain.embedded.MemberProvider
import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL

@Entity
@Table(
    name = "members",
    uniqueConstraints =
        [
            UniqueConstraint(name = "UNQ_PROVIDER", columnNames = ["provider_id", "provider_type"]),
            UniqueConstraint(name = "UNQ_EMAIL", columnNames = ["email"]),
            UniqueConstraint(name = "UNQ_HANDLE", columnNames = ["handle"])
        ],
)
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val email: String,

    @Column(length = 20)
    var handle: String? = null, // 사용자 지정 공개 식별자 (트위터 @username 같은 역할)

    @Version
    val version: Long = 0,

    var agreeTerm: Boolean = false,
    var nickname: String,
    var role: Role = Role.MEMBER,
    var init: Boolean = false, // false면 가입중(정보입력중), true면 가입완료

    @Embedded
    var introduction: MemberIntroduction? = null,

    @Embedded
    var personality: MemberPersonality? = null,

    @Embedded
    var location: MemberLocation? = null,

    @Embedded
    var provider: MemberProvider,

    @Embedded
    var audit: MemberAudit,

    @ElementCollection
    @CollectionTable(name = "member_languages", joinColumns = [JoinColumn(name = "member_id")])
    val languages: MutableSet<MemberLanguage> = mutableSetOf(),

    @OneToMany(cascade = [ALL], orphanRemoval = true)
    @JoinColumn(name = "member_id")
    val images: MutableList<MemberImage> = mutableListOf(),
) {
    val isDeleted: Boolean
        get() = audit.isDeleted

    /** 초기화 완료 가능 여부 (필수 정보가 모두 입력되었는지) */
    val isReadyToFinishInit: Boolean
        get() = handle != null &&
                personality != null &&
                location != null &&
                introduction != null &&
                languages.isNotEmpty()

    fun login() {
        audit.login()
    }

    fun upgradeToPremium() {
        role = Role.PREMIUM
    }

    fun delete() {
        audit.delete()
    }

    enum class Role { MEMBER, PREMIUM, ADMIN }
    companion object {
        private val HANDLE_PATTERN = Regex("^[a-zA-Z0-9_]{3,20}$")

        fun isValidHandle(handle: String): Boolean = HANDLE_PATTERN.matches(handle)

        fun create(
            nickname: String,
            email: String,
            providerId: String,
            providerType: String,
            providerUserName: String
        ) = Member(
            nickname = nickname,
            email = email,
            provider = MemberProvider(providerId, MemberProvider.Type.valueOf(providerType), providerUserName),
            audit = MemberAudit()
        )
    }
}
