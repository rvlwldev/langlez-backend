package com.langlez.member2.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "member_audits")
class MemberAudit(@Id val memberId: Long) {
    val createdAt: Instant = Instant.now()
    var verifiedAt: Instant? = null
    var lastHandleUpdatedAt: Instant? = null
    var suspendedAt: Instant? = null
    var withdrawnAt: Instant? = null
}
