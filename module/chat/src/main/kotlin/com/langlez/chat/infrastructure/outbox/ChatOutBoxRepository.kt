package com.langlez.chat.infrastructure.outbox

interface ChatOutBoxRepository {

    fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): ChatOutBox

    fun findToDispatch(limit: Int): List<ChatOutBox>
    fun findAllCompleted(): List<ChatOutBox>

    fun saveAll(outboxes: List<ChatOutBox>): List<ChatOutBox>
    fun deleteAll(outboxes: List<ChatOutBox>)
    fun saveAllHistory(history: List<ChatOutBoxHistory>)
}
