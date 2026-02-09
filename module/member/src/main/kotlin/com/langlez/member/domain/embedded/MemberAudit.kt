package com.langlez.member.domain.embedded

import com.langlez.common.CommonAudit
import jakarta.persistence.Embeddable
import java.time.Instant

@Embeddable
class MemberAudit(var lastLoggedInAt: Instant? = null) : CommonAudit() {
    fun login() {
        lastLoggedInAt = Instant.now()
    }

    /** 깊은 복사 (Member PK 변경 시 사용) */
    fun copy(): MemberAudit =
            MemberAudit(lastLoggedInAt).also {
                it.createdAt = this.createdAt
                it.updatedAt = this.updatedAt
                it.deletedAt = this.deletedAt
            }
}
