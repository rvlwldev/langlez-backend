package com.langlez.member.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "member_audits")
class MemberAudit(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var lastAccessedIp: String? = null,
    var verifiedAt: Instant? = null,
    var agreedTermsAt: Instant? = null,
    var lastHandleUpdatedAt: Instant? = null,
    var lastNicknameUpdatedAt: Instant? = null,
    var lastAccessedAt: Instant? = null,
    var suspendedAt: Instant? = null,
    var withdrawnAt: Instant? = null,
)
