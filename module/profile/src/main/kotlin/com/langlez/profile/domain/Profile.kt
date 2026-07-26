package com.langlez.profile.domain

import com.langlez.member.domain.Member
import jakarta.persistence.*
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.FetchType.LAZY
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "member_profiles")
class Profile(
    @Id val id: Long,
    @MapsId @JoinColumn(name = "id")
    @OneToOne(fetch = LAZY) val member: Member,

    @Column(length = 1000) var bio: String? = null,
    @Column(length = 1000) var goal: String? = null,
    @Column(length = 1000) var want: String? = null,
    @Enumerated(STRING) var gender: Gender = Gender.SECRET,
    @Enumerated(STRING) var mbti: MBTI? = null,

    @Convert(converter = CountryConverter::class)
    var locale: Locale? = null,
    var birthDay: LocalDate? = null,
    var visitCount: Long = 0,

    @Enumerated(STRING) var languageLevel: LanguageLevel? = null,

    @Version var version: Long? = null
) {
    enum class Gender { MALE, FEMALE, SECRET }
    enum class MBTI {
        ENFJ, ENFP, ENTJ, ENTP,
        ESFJ, ESFP, ESTJ, ESTP,
        INFJ, INFP, INTJ, INTP,
        ISFJ, ISFP, ISTJ, ISTP,
    }
    enum class LanguageLevel { BEGINNER, INTERMEDIATE, ADVANCED }
}
