package com.langlez.relationship.infrastructure.outbox

interface RelationshipOutBoxRepository {

    fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): RelationshipOutBox

    fun findToDispatch(limit: Int): List<RelationshipOutBox>
    fun findAllCompleted(): List<RelationshipOutBox>

    fun saveAll(outboxes: List<RelationshipOutBox>): List<RelationshipOutBox>
    fun deleteAll(outboxes: List<RelationshipOutBox>)
    fun saveAllHistory(history: List<RelationshipOutBoxHistory>)
}
