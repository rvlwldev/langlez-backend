package com.langlez.member.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class MemberOutBoxRepository(
    private val jpa: MemberOutBoxJpaRepository,
    private val historyJpa: MemberOutBoxHistoryJpaRepository,
    private val mapper: ObjectMapper,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): MemberOutBox =
        jpa.save(MemberOutBox(aggregateType, aggregateId, eventName, mapper.writeValueAsString(payload)))

    fun findToDispatch(limit: Int): List<MemberOutBox> =
        jpa.findAllByStatusInOrderByCreatedAtAsc(
            listOf(MemberOutBox.Status.READY, MemberOutBox.Status.PROCESSING),
            PageRequest.of(0, limit),
        )

    fun findAllCompleted(): List<MemberOutBox> =
        jpa.findAllByStatus(MemberOutBox.Status.COMPLETE)

    fun saveAll(outboxes: List<MemberOutBox>): List<MemberOutBox> = jpa.saveAll(outboxes)

    fun deleteAll(outboxes: List<MemberOutBox>) = jpa.deleteAll(outboxes)

    fun saveAllHistory(history: List<MemberOutBoxHistory>) { historyJpa.saveAll(history) }
}

interface MemberOutBoxJpaRepository : JpaRepository<MemberOutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(
        statuses: List<MemberOutBox.Status>,
        pageable: org.springframework.data.domain.Pageable,
    ): List<MemberOutBox>
    fun findAllByStatus(status: MemberOutBox.Status): List<MemberOutBox>
}

interface MemberOutBoxHistoryJpaRepository : JpaRepository<MemberOutBoxHistory, Long>
