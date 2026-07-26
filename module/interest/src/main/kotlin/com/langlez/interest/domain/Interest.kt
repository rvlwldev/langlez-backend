package com.langlez.interest.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "interests")
class Interest(
    var ko: String? = null,
    var en: String? = null,
    var ja: String? = null,
    // Hibernate 기본 네이밍 전략이 zhTW/zhCN을 zhtw/zhcn(언더스코어 없이)으로 매핑하므로
    // camelToSnake 기반 native DDL/검색 쿼리와 어긋나지 않도록 컬럼명을 명시적으로 고정한다.
    @Column(name = "zh_tw") var zhTW: String? = null,
    @Column(name = "zh_cn") var zhCN: String? = null,
    var de: String? = null,
    var vi: String? = null,
    var ind: String? = null,
    var fr: String? = null,
    var pt: String? = null,
    var es: String? = null,
    var ru: String? = null,
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0

    /** locale 필드명(ko/en/...)으로 현재 값을 읽는다. */
    fun get(localeField: String): String? = when (localeField) {
        "ko" -> ko; "en" -> en; "ja" -> ja; "zhTW" -> zhTW; "zhCN" -> zhCN; "de" -> de
        "vi" -> vi; "ind" -> ind; "fr" -> fr; "pt" -> pt; "es" -> es; "ru" -> ru
        else -> null
    }

    /** locale 필드명으로 값을 설정한다. */
    fun set(localeField: String, value: String?) {
        when (localeField) {
            "ko" -> ko = value; "en" -> en = value; "ja" -> ja = value
            "zhTW" -> zhTW = value; "zhCN" -> zhCN = value; "de" -> de = value
            "vi" -> vi = value; "ind" -> ind = value; "fr" -> fr = value
            "pt" -> pt = value; "es" -> es = value; "ru" -> ru = value
        }
    }

    companion object {
        /** FULLTEXT 인덱스를 만들어야 하는 언어 컬럼 전체(DB 컬럼명 기준, camelCase가 스네이크로 매핑됨). */
        val LOCALE_FIELDS = listOf("ko", "en", "ja", "zhTW", "zhCN", "de", "vi", "ind", "fr", "pt", "es", "ru")
    }
}
