package com.langlez.member.infrastructure.outbox

interface MemberOutBoxRepository {

    fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): MemberOutBox

    fun findToDispatch(limit: Int): List<MemberOutBox>
    fun findAllCompleted(): List<MemberOutBox>

    fun saveAll(outboxes: List<MemberOutBox>): List<MemberOutBox>
    fun deleteAll(outboxes: List<MemberOutBox>)
    fun saveAllHistory(history: List<MemberOutBoxHistory>)
}
