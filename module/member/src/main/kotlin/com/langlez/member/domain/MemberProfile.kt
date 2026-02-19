package com.langlez.member.domain

import com.langlez.member.domain.embedded.*
import jakarta.persistence.*

@Entity
@Table(name = "member_profiles")
class MemberProfile(
    @Id val memberId: Long? = null,

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    val member: Member,

    @Embedded var introduction: MemberIntroduction? = null,
    @Embedded var personality: MemberPersonality? = null,
    @Embedded var location: MemberLocation? = null,

    @ElementCollection
    @CollectionTable(name = "member_languages", joinColumns = [JoinColumn(name = "member_id")])
    val languages: MutableSet<MemberLanguage> = mutableSetOf(),
) {
    val isReadyToFinishInit: Boolean
        get() = member.username != null &&
                personality != null &&
                location != null &&
                introduction != null &&
                languages.isNotEmpty() &&
                member.images.isNotEmpty()
}
