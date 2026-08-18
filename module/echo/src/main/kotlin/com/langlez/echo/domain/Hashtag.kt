package com.langlez.echo.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "hashtags",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_HASHTAG_NAME", columnNames = ["name"])]
)
class Hashtag(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val name: String,

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    companion object {
        const val MAX_PER_POST = 10

        /**
         * `#` 뒤 글자·숫자·밑줄만 태그로 본다. `\w` 는 ASCII 만 잡아서 한글·일본어 태그가 통째로 날아간다.
         *
         * 소문자로 모아 저장한다 — `#Seoul` 과 `#seoul` 이 다른 행이 되면 검색이 갈라진다.
         * 한 글에 같은 태그를 여러 번 써도 `PostHashtag` 유니크 제약에 걸리므로 여기서 중복을 걷어낸다.
         */
        private val PATTERN = Regex("#([\\p{L}\\p{N}_]+)")

        fun extract(content: String): Set<String> = PATTERN.findAll(content)
            .map { it.groupValues[1].lowercase() }
            .toSet()
            .take(MAX_PER_POST)
            .toSet()
    }
}
