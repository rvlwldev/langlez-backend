package com.langlez.relationship.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.relationship.domain.RelationshipOutBox
import com.langlez.relationship.domain.RelationshipOutBoxHistory
import com.langlez.relationship.domain.RelationshipOutBoxRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class RelationshipOutBoxRepositoryImpl(
    private val jpa: RelationshipOutBoxJpaRepository,
    private val historyJpa: RelationshipOutBoxHistoryJpaRepository,
    private val mapper: ObjectMapper,
) : RelationshipOutBoxRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): RelationshipOutBox =
        jpa.save(RelationshipOutBox(aggregateType, aggregateId, eventName, mapper.writeValueAsString(payload)))

    override fun findToDispatch(limit: Int): List<RelationshipOutBox> =
        jpa.findAllByStatusInOrderByCreatedAtAsc(
            listOf(RelationshipOutBox.Status.READY, RelationshipOutBox.Status.PROCESSING),
            PageRequest.of(0, limit),
        )

    override fun findAllCompleted(): List<RelationshipOutBox> =
        jpa.findAllByStatus(RelationshipOutBox.Status.COMPLETE)

    override fun saveAll(outboxes: List<RelationshipOutBox>): List<RelationshipOutBox> = jpa.saveAll(outboxes)

    override fun deleteAll(outboxes: List<RelationshipOutBox>) = jpa.deleteAll(outboxes)

    override fun saveAllHistory(history: List<RelationshipOutBoxHistory>) { historyJpa.saveAll(history) }
}

interface RelationshipOutBoxJpaRepository : JpaRepository<RelationshipOutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(
        statuses: List<RelationshipOutBox.Status>,
        pageable: org.springframework.data.domain.Pageable,
    ): List<RelationshipOutBox>
    fun findAllByStatus(status: RelationshipOutBox.Status): List<RelationshipOutBox>
}

interface RelationshipOutBoxHistoryJpaRepository : JpaRepository<RelationshipOutBoxHistory, Long>
