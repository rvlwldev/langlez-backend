package com.langlez.echo.infrastructure.outbox

interface EchoOutBoxRepository {

    fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): EchoOutBox
    fun save(command: CreateOutBoxCommand): EchoOutBox = save(command.aggregateType, command.aggregateId, command.eventName, command.payload)

    fun findToDispatch(limit: Int): List<EchoOutBox>
    fun findAllCompleted(): List<EchoOutBox>
    fun findCompletedOrFailed(limit: Int): List<EchoOutBox>

    fun saveAll(outboxes: List<EchoOutBox>): List<EchoOutBox>
    fun deleteAll(outboxes: List<EchoOutBox>)
    fun saveAllHistory(history: List<EchoOutBoxHistory>)
}

data class CreateOutBoxCommand(
    val aggregateType: String,
    val aggregateId: String,
    val eventName: String,
    val payload: Any?
)
