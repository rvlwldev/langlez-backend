package com.langlez.member.domain.embedded

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.time.LocalDate
import java.util.Locale

@Embeddable
data class MemberPersonality(
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Column(name = "birth_day")
    var birthDay: LocalDate? = null,

    @Convert(converter = NationalityConverter::class)
    @Column(name = "nationality", length = 3)
    var nationality: Nationality? = null,

    @Column(name = "gender", length = 10)
    var gender: Gender? = null,

    @Column(name = "mbti", length = 4)
    var mbti: MBTI? = null
) {
    enum class Gender { MALE, FEMALE, SECRET }
    enum class MBTI {
        ENFJ, ENFP, ENTJ, ENTP,
        ESFJ, ESFP, ESTJ, ESTP,
        INFJ, INFP, INTJ, INTP,
        ISFJ, ISFP, ISTJ, ISTP,
    }

    @Embeddable
    data class Nationality(val code: String = "") : Serializable {
        companion object {
            private val ISO_COUNTRIES = Locale.getISOCountries().toSet()

            fun isValid(code: String) = ISO_COUNTRIES.contains(code.uppercase())

            fun of(code: String): Nationality? =
                if (isValid(code)) Nationality(code.uppercase()) else null
        }
    }
}
