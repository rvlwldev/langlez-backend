package com.langlez.member2.domain

import com.langlez.member.domain.MemberAudit
import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.FetchType.LAZY
import java.time.LocalDate

@Entity
@Table(
    name = "members",
    uniqueConstraints = [
        UniqueConstraint("UNQ_MEMBER_EMAIL", ["email"]),
        UniqueConstraint("UNQ_MEMBER_HANDLE", ["handle"]),
    ]
)
class Member(
    val email: String,
    @Column(length = 20) var handle: String,
    @Column(length = NICKNAME_MAX_LENGTH) var nickname: String,
    val birthday: LocalDate,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(STRING)
    var status: Status = Status.CREATED

    @Enumerated(STRING)
    var role: Role = Role.MEMBER

    var gender: Gender = Gender.SECRET

    var fcm: String? = null

    @OneToOne(fetch = LAZY, cascade = [ALL], orphanRemoval = true)
    @JoinColumn(name = "member_audit_id")
    val audit: MemberAudit = MemberAudit()

    @Version
    var version: Long = 0

    enum class Status { CREATED, ACTIVE, SUSPENDED, WITHDRAWN; }
    enum class Gender { SECRET, MALE, FEMALE; }
    enum class Role {
        MEMBER, PREMIUM, ADMIN;
        val authority: String get() = "ROLE_$name"
    }

    companion object {
        const val HANDLE_REGEX = "^[a-zA-Z0-9_.]{3,20}$"
        const val NICKNAME_MAX_LENGTH = 20
    }
}