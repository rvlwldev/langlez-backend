package com.langlez.member.domain.embedded

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Embeddable
data class MemberLanguage(
        @Column(nullable = false) @Enumerated(EnumType.STRING) val language: Language, // ISO 639-1
        @Column(nullable = false) @Enumerated(EnumType.STRING) val level: Level,
) {
    enum class Level {
        BEGINNER,
        ELEMENTARY,
        MIDDLE,
        ADVANCED,
        NATIVE
    }

    enum class Language(@get:JsonValue val code: String) {
        KOREAN("ko"),
        ENGLISH("en"),
        FRENCH("fr"),
        SPANISH("es"),
        GERMAN("de"),
        RUSSIAN("ru"),
        CHINESE("zh"),
        JAPANESE("ja");

        companion object {
            @JvmStatic
            @JsonCreator
            fun of(code: String): Language =
                    entries.find { it.code.equals(code, ignoreCase = true) }
                            ?: throw IllegalArgumentException("Unknown language code: $code")
        }
    }
}
