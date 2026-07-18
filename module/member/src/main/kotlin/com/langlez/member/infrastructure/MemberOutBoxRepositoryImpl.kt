package com.langlez.member.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.member.domain.MemberOutBox
import com.langlez.member.domain.MemberOutBoxHistory
import com.langlez.member.domain.MemberOutBoxRepository
import com.langlez.member.infrastructure.jpa.MemberOutBoxHistoryJpaRepository
import com.langlez.member.infrastructure.jpa.MemberOutBoxJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class MemberOutBoxRepositoryImpl(
    private val jpa: MemberOutBoxJpaRepository,
    private val historyJpa: MemberOutBoxHistoryJpaRepository,
    private val mapper: ObjectMapper,
) : MemberOutBoxRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): MemberOutBox =
        jpa.save(MemberOutBox(aggregateType, aggregateId, eventName, mapper.writeValueAsString(payload)))

    override fun findToDispatch(limit: Int): List<MemberOutBox> =
        jpa.findAllByStatusInOrderByCreatedAtAsc(
            listOf(MemberOutBox.Status.READY, MemberOutBox.Status.PROCESSING),
            PageRequest.of(0, limit),
        )

    override fun findAllCompleted(): List<MemberOutBox> =
        jpa.findAllByStatus(MemberOutBox.Status.COMPLETE)

    override fun saveAll(outboxes: List<MemberOutBox>): List<MemberOutBox> = jpa.saveAll(outboxes)

    override fun deleteAll(outboxes: List<MemberOutBox>) = jpa.deleteAll(outboxes)

    override fun saveAllHistory(history: List<MemberOutBoxHistory>) { historyJpa.saveAll(history) }
}
