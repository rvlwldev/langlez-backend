package com.langlez.rdb.search

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 검색 기반(V10)이 실제로 동작하는지 검증하기 위한 테스트 전용 테이블.
 * 프로덕션 스키마(Flyway)가 아니라 이 테스트가 직접 만들고 지운다 (TrgmSearchIntegrationTest 참고).
 */
@Entity
@Table(name = "search_spike_documents")
class SearchDocument(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,
    val content: String,
)
