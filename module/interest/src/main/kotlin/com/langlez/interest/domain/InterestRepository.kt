package com.langlez.interest.domain

interface InterestRepository {
    fun findById(id: Long): Interest?
    fun findByColumn(localeField: String, value: String): Interest?
    fun save(interest: Interest): Interest
    fun delete(interest: Interest)
    /** locale 컬럼 하나에 대해 FULLTEXT 검색(MATCH...AGAINST), 상위 limit개. */
    fun searchByColumn(localeField: String, term: String, limit: Int): List<Interest>
}
