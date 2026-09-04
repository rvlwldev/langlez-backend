package com.langlez.lang.domain

import jakarta.persistence.*
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.GenerationType.IDENTITY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * 회원의 언어 프로필 한 줄. "이 언어를 모국어로 한다" 또는 "이 언어를 이 레벨로 배운다".
 *
 * 원래 `Profile.languageLevel` 하나뿐이라 **어떤 언어의 레벨인지가 없었다.** 그래서 매칭이
 * 원리적으로 불가능했다. 언어를 행으로 쪼개 회원당 여러 개를 갖게 한 것이 이 엔티티다.
 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "member_languages",
    // (member_id, language, role) 이 아니라 (member_id, language) 다. role 을 키에 넣으면
    // 같은 언어를 모국어이자 학습언어로 동시에 등록할 수 있고, 그러면 매칭에서
    // 자기 자신과 상호보완이 성립한다.
    uniqueConstraints = [UniqueConstraint(name = "UNQ_MEMBER_LANGUAGE", columnNames = ["member_id", "language"])]
)
class MemberLanguage(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    // BCP-47. enum 으로 두지 않는다 — 지원 언어가 늘 때 마이그레이션 없이 상수만 고치면 된다.
    @Column(nullable = false, length = 10)
    val language: String,

    @Enumerated(STRING) @Column(nullable = false)
    val role: Role,

    @Enumerated(STRING)
    var level: Level? = null,

    @CreatedDate @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    init {
        require(language in SUPPORTED) { "lang.unsupported" }
        // NATIVE 에 레벨을 두면 "모국어 초급"이 만들어지고 매칭이 그걸 후보로 잡는다.
        require((role == Role.LEARNING) == (level != null)) { "lang.level.invalid" }
    }

    enum class Role { NATIVE, LEARNING }

    /**
     * **상수 순서가 의미를 갖는다.** 매칭의 레벨 근접도가 ordinal 차이로 계산된다.
     * 순서를 바꾸거나 중간에 값을 끼워 넣으면 매칭이 조용히 틀어진다.
     */
    enum class Level { BEGINNER, INTERMEDIATE, ADVANCED }

    companion object {
        /**
         * `common` 의 i18n 번들(`messages_*.properties`) 12개와 짝을 맞춘다.
         *
         * 표기는 BCP-47 이라 i18n 파일명(`messages_zh_CN`)과 구분자가 다르다. 파일명을 그대로
         * 복사하면 `zh_CN` 이 들어와 클라이언트가 보내는 `zh-CN` 이 전부 거부된다.
         */
        val SUPPORTED = setOf("de", "en", "es", "fr", "id", "ja", "ko", "pt", "ru", "vi", "zh-CN", "zh-TW")

        /** 모국어 상한. DB 는 못 막으므로 서비스가 검사한다. */
        const val MAX_NATIVE = 3

        /** 학습언어 상한. DB 는 못 막으므로 서비스가 검사한다. */
        const val MAX_LEARNING = 5
    }
}
