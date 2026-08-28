package com.langlez.profile.domain

import jakarta.persistence.*
import jakarta.persistence.EnumType.STRING

@Entity
@Table(name = "member_profiles")
class Profile(
    // 회원 id 를 그대로 쓴다. member_profiles.id 는 여전히 members(id) 로의 FK 지만,
    // 물리적 무결성은 DB 에 맡기고 JPA 매핑에서는 member 모듈을 참조하지 않는다.
    @Id val id: Long,

    @Column(length = 1000) var bio: String? = null,
    @Column(length = 1000) var goal: String? = null,
    @Column(length = 1000) var want: String? = null,
    @Enumerated(STRING) var mbti: MBTI? = null,

    var visitCount: Long = 0,

    @Enumerated(STRING) var languageLevel: LanguageLevel? = null,

    @Version var version: Long? = null
) {
    enum class MBTI {
        ENFJ, ENFP, ENTJ, ENTP,
        ESFJ, ESFP, ESTJ, ESTP,
        INFJ, INFP, INTJ, INTP,
        ISFJ, ISFP, ISTJ, ISTP,
    }
    enum class LanguageLevel { BEGINNER, INTERMEDIATE, ADVANCED }
}
