package com.langlez.member2.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "member_providers",
    uniqueConstraints = [UniqueConstraint("UNQ_MEMBER_PROVIDER", ["provider_id", "provider_type"])]
)
class MemberProvider(
    val memberId: Long,
    val providerType: ProviderType,
    val providerId: String,
    val providerDisplayName: String
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    enum class ProviderType { APPLE, GOOGLE; }
}