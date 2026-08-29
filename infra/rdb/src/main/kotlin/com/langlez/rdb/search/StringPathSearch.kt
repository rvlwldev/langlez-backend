package com.langlez.rdb.search

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.StringPath

// CJK 2글자 단어(日本/中文/공부/서울)를 못 찾는 걸 막으려 3자가 아니라 2자로 잡는다.
// 2자는 GIN 인덱스를 못 타 Seq Scan 이 되지만 성능보다 정확성이 우선이다.
const val MIN_SEARCH_LENGTH = 2

/**
 * pg_trgm + unaccent(V10 의 f_unaccent) 기반 전 언어 부분일치 검색.
 *
 * 컬럼과 검색어 양쪽을 반드시 같은 f_unaccent() 로 감싸야 컬럼에 건 함수 인덱스를 탄다.
 * QueryDSL 의 StringExpression.contains() 는 like '%x%' 만 만들 뿐 컬럼을 감쌀 방법이 없어 쓸 수 없다.
 *
 * contains() 가 대신 해주던 %/_/\ 이스케이프는 직접 한다 - 안 하면 "100%" 같은 정상 입력이
 * 와일드카드로 해석돼 의도치 않은 전체 매칭이나 성능 저하가 된다.
 */
fun StringPath.search(term: String): BooleanExpression {
    val trimmed = term.trim()
    require(trimmed.length >= MIN_SEARCH_LENGTH) { "validation.search.min-length" }

    val escaped = trimmed
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    // f_unaccent 는 Hibernate 메타데이터에 등록되지 않은 함수라 function() 호출의 반환 타입을
    // Object 로 추론한다. like 는 string 타입만 받으므로 cast(... as string) 으로 명시해야 한다.
    val unaccentedColumn = Expressions.stringTemplate("cast(function('f_unaccent', {0}) as string)", this)
    val unaccentedTerm =
        Expressions.stringTemplate("cast(function('f_unaccent', {0}) as string)", Expressions.constant("%$escaped%"))

    return Expressions.booleanTemplate("{0} like {1} escape '\\'", unaccentedColumn, unaccentedTerm)
}
