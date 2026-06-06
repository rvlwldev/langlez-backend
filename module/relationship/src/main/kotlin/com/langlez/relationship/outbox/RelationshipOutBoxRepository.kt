package com.langlez.relationship.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class RelationshipOutBoxRepository(
    private val jpa: RelationshipOutBoxJpaRepository,
    private val historyJpa: RelationshipOutBoxHistoryJpaRepository,
    private val mapper: ObjectMapper,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): RelationshipOutBox =
        jpa.save(RelationshipOutBox(aggregateType, aggregateId, eventName, mapper.writeValueAsString(payload)))

    fun findToDispatch(limit: Int): List<RelationshipOutBox> =
        jpa.findAllByStatusInOrderByCreatedAtAsc(
            listOf(RelationshipOutBox.Status.READY, RelationshipOutBox.Status.PROCESSING),
            PageRequest.of(0, limit),
        )

    fun findAllCompleted(): List<RelationshipOutBox> =
        jpa.findAllByStatus(RelationshipOutBox.Status.COMPLETE)

    fun saveAll(outboxes: List<RelationshipOutBox>): List<RelationshipOutBox> = jpa.saveAll(outboxes)

    fun deleteAll(outboxes: List<RelationshipOutBox>) = jpa.deleteAll(outboxes)

    fun saveAllHistory(history: List<RelationshipOutBoxHistory>) { historyJpa.saveAll(history) }
}

interface RelationshipOutBoxJpaRepository : JpaRepository<RelationshipOutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(
        statuses: List<RelationshipOutBox.Status>,
        pageable: org.springframework.data.domain.Pageable,
    ): List<RelationshipOutBox>
    fun findAllByStatus(status: RelationshipOutBox.Status): List<RelationshipOutBox>
}

interface RelationshipOutBoxHistoryJpaRepository : JpaRepository<RelationshipOutBoxHistory, Long>
