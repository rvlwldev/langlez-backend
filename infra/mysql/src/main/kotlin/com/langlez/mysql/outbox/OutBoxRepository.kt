package com.langlez.mysql.outbox

/**
 * OutBox 스케줄러가 요구하는 최소 저장소 계약.
 * 각 모듈은 자기 엔티티 타입으로 이 인터페이스를 구현한다.
 */
interface OutBoxRepository<T : AbstractOutBox, H : AbstractOutBoxHistory> {

    fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): T

    fun save(command: CreateOutBoxCommand): T =
        save(command.aggregateType, command.aggregateId, command.eventName, command.payload)

    /** 아직 발행되지 않은(READY/PROCESSING) 이벤트를 오래된 순으로 limit 만큼 조회한다. */
    fun findToDispatch(limit: Int): List<T>

    /** 이관 대상(COMPLETE/FAILED)을 limit 만큼 조회한다. 전량 로드로 인한 OOM을 피하기 위해 반드시 페이징한다. */
    fun findCompletedOrFailed(limit: Int): List<T>

    fun saveAll(outboxes: List<T>): List<T>

    fun deleteAll(outboxes: List<T>)

    fun saveAllHistory(history: List<H>)
}

data class CreateOutBoxCommand(
    val aggregateType: String,
    val aggregateId: String,
    val eventName: String,
    val payload: Any?,
)
