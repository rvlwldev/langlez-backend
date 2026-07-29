package com.langlez.relationship.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.mysql.outbox.OutBoxStatus
import com.langlez.relationship.infrastructure.outbox.jpa.RelationshipOutBoxHistoryJpaRepository
import com.langlez.relationship.infrastructure.outbox.jpa.RelationshipOutBoxJpaRepository
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
    override fun save(domain: String, topic: String, payload: String?, key: Any?): RelationshipOutBox {
        val payloadString = payload ?: ""
        val keyString = key?.toString()
        return jpa.save(RelationshipOutBox(domain, topic, payloadString, keyString))
    }

    override fun save(outbox: RelationshipOutBox): RelationshipOutBox = jpa.save(outbox)

    override fun findToDispatch(limit: Int): List<RelationshipOutBox> =
        jpa.findAllByStatusInOrderByCreatedAtAsc(
            listOf(OutBoxStatus.READY, OutBoxStatus.PROCESSING),
            PageRequest.of(0, limit),
        )

    override fun findAllProcessed(limit: Int): List<RelationshipOutBox> =
        jpa.findAllByStatusIn(
            listOf(OutBoxStatus.COMPLETE, OutBoxStatus.FAILED),
            PageRequest.of(0, limit),
        )

    override fun saveAll(outboxes: List<RelationshipOutBox>): List<RelationshipOutBox> = jpa.saveAll(outboxes)

    override fun deleteAll(outboxes: List<RelationshipOutBox>) { jpa.deleteAll(outboxes) }

    override fun saveHistory(history: RelationshipOutBoxHistory): RelationshipOutBoxHistory = historyJpa.save(history)

    override fun saveAllHistory(history: List<RelationshipOutBoxHistory>) { historyJpa.saveAll(history) }
}
