package com.langlez.relationship.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.relationship.infrastructure.outbox.jpa.RelationshipOutBoxHistoryJpaRepository
import com.langlez.relationship.infrastructure.outbox.jpa.RelationshipOutBoxJpaRepository
import com.langlez.mysql.outbox.OutBoxStatus
import org.springframework.data.domain.PageRequest
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
            listOf(OutBoxStatus.READY, OutBoxStatus.PROCESSING),
            PageRequest.of(0, limit),
        )

    override fun findCompletedOrFailed(limit: Int): List<RelationshipOutBox> =
        jpa.findAllByStatusIn(
            listOf(OutBoxStatus.COMPLETE, OutBoxStatus.FAILED),
            PageRequest.of(0, limit),
        )

    override fun saveAll(outboxes: List<RelationshipOutBox>): List<RelationshipOutBox> = jpa.saveAll(outboxes)

    override fun deleteAll(outboxes: List<RelationshipOutBox>) = jpa.deleteAll(outboxes)

    override fun saveAllHistory(history: List<RelationshipOutBoxHistory>) { historyJpa.saveAll(history) }
}
