package com.langlez.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class MemberProvider(
    @Column(name = "provider_id") val id: String,
    @Column(name = "provider_type") val type: Type,
    @Column(name = "provider_username") val username: String?
) {
    constructor(id: String, type: String, username: String?) : this(id, Type.valueOf(type.uppercase()), username)
    enum class Type { GOOGLE, APPLE }
}