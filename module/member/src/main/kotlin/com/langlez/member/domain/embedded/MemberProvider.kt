package com.langlez.member.domain.embedded

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class MemberProvider(
        @Column(name = "provider_id") val id: String,
        @Column(name = "provider_type") val type: Type,
        @Column(name = "provider_username") val username: String?
) {
    enum class Type {
        GOOGLE,
        APPLE
    }
}
