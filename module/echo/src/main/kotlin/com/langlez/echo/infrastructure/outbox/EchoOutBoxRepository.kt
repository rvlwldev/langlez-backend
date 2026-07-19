package com.langlez.echo.infrastructure.outbox

interface EchoOutBoxRepository {

    fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): EchoOutBox

    fun findToDispatch(limit: Int): List<EchoOutBox>
    fun findAllCompleted(): List<EchoOutBox>

    fun saveAll(outboxes: List<EchoOutBox>): List<EchoOutBox>
    fun deleteAll(outboxes: List<EchoOutBox>)
    fun saveAllHistory(history: List<EchoOutBoxHistory>)
}
