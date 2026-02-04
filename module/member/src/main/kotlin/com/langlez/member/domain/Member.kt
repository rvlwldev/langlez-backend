package com.langlez.member.domain

import com.langlez.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "members")
class Member(
    @Column(nullable = false, unique = true)
    val email: String,
    @Column(nullable = false)
    var nickname: String,
    @Column(name = "profile_image_url")
    var profileImageUrl: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: MemberRole = MemberRole.MEMBER,
    @Column(nullable = false)
    val provider: String,
    @Column(nullable = false)
    val providerId: String,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    fun upgradeToPremium() {
        this.role = MemberRole.PREMIUM
    }
}
