package com.langlez.mysql.outbox

/**
 * OutBox 스케줄러가 요구하는 최소 저장소 계약.
 * 각 모듈은 자기 엔티티 타입으로 이 인터페이스를 구현한다.
 */
interface OutBoxRepository<T : OutBox, H : OutBoxHistory> {

    fun save(type: String, topic: String, payload: String?, key: Any?): T
    fun save(outbox: T): T
    fun saveAll(outboxes: List<T>): List<T>

    fun saveHistory(history: H): H
    fun saveAllHistory(history: List<H>)

    /** 아직 발행되지 않은(READY/PROCESSING) 이벤트를 오래된 순으로 limit 만큼 조회한다. */
    fun findToDispatch(limit: Int): List<T>

    /** 이관 대상(COMPLETE/FAILED)을 limit 만큼 조회한다. 전량 로드로 인한 OOM을 피하기 위해 반드시 페이징한다. */
    fun findAllProcessed(limit: Int): List<T>

    fun deleteAll(outboxes: List<T>)
}