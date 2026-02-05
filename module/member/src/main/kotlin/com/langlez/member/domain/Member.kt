package com.langlez.member.domain

import com.langlez.common.BaseTimeEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
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

    @ElementCollection
    @CollectionTable(name = "member_profile_images", joinColumns = [JoinColumn(name = "member_id")])
    @Column(name = "image_url")
    var additionalProfileImages: MutableList<String> = mutableListOf(),
    @Column(name = "location_country")
    var locationCountry: String? = null,
    @Column(name = "location_city")
    var locationCity: String? = null,
    @Column(name = "nationality")
    var nationality: String? = null,
    @ElementCollection
    @CollectionTable(name = "member_interests", joinColumns = [JoinColumn(name = "member_id")])
    @Column(name = "interest")
    var interests: MutableList<String> = mutableListOf(),
    @Column(name = "mbti")
    var mbti: String? = null,
    @Column(name = "native_language")
    var nativeLanguage: String? = null,
    @ElementCollection
    @CollectionTable(name = "member_target_languages", joinColumns = [JoinColumn(name = "member_id")])
    var targetLanguages: MutableList<TargetLanguage> = mutableListOf(),
    @ElementCollection
    @CollectionTable(name = "member_wish_destinations", joinColumns = [JoinColumn(name = "member_id")])
    @Column(name = "destination")
    var wishDestinations: MutableList<String> = mutableListOf(),
    @ElementCollection
    @CollectionTable(name = "member_visited_destinations", joinColumns = [JoinColumn(name = "member_id")])
    @Column(name = "destination")
    var visitedDestinations: MutableList<String> = mutableListOf(),
    @Column(name = "is_online")
    var isOnline: Boolean = false,
    @Column(name = "agreed_to_terms")
    var agreedToTerms: Boolean = false,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    fun upgradeToPremium() {
        this.role = MemberRole.PREMIUM
    }

    fun updateProfile(
        nickname: String,
        profileImageUrl: String?,
        additionalProfileImages: List<String>?,
        locationCountry: String?,
        locationCity: String?,
        nationality: String?,
        interests: List<String>?,
        mbti: String?,
        nativeLanguage: String?,
        targetLanguages: List<TargetLanguage>?,
        wishDestinations: List<String>?,
        visitedDestinations: List<String>?,
    ) {
        this.nickname = nickname
        this.profileImageUrl = profileImageUrl
        additionalProfileImages?.let {
            this.additionalProfileImages.clear()
            this.additionalProfileImages.addAll(it)
        }
        this.locationCountry = locationCountry
        this.locationCity = locationCity
        this.nationality = nationality
        interests?.let {
            this.interests.clear()
            this.interests.addAll(it)
        }
        this.mbti = mbti
        this.nativeLanguage = nativeLanguage
        targetLanguages?.let {
            this.targetLanguages.clear()
            this.targetLanguages.addAll(it)
        }
        wishDestinations?.let {
            this.wishDestinations.clear()
            this.wishDestinations.addAll(it)
        }
        visitedDestinations?.let {
            this.visitedDestinations.clear()
            this.visitedDestinations.addAll(it)
        }
    }
}

@Embeddable
data class TargetLanguage(
    @Column(name = "language")
    val language: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "level")
    val level: LanguageLevel,
)

enum class LanguageLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}
