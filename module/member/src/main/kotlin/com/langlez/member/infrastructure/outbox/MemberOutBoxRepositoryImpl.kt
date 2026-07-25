package com.langlez.member.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.member.infrastructure.outbox.jpa.MemberOutBoxHistoryJpaRepository
import com.langlez.member.infrastructure.outbox.jpa.MemberOutBoxJpaRepository
import com.langlez.mysql.outbox.OutBoxStatus
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
            listOf(OutBoxStatus.READY, OutBoxStatus.PROCESSING),
            PageRequest.of(0, limit),
        )

    override fun findCompletedOrFailed(limit: Int): List<MemberOutBox> =
        jpa.findAllByStatusIn(
            listOf(OutBoxStatus.COMPLETE, OutBoxStatus.FAILED),
            PageRequest.of(0, limit),
        )

    override fun saveAll(outboxes: List<MemberOutBox>): List<MemberOutBox> = jpa.saveAll(outboxes)

    override fun deleteAll(outboxes: List<MemberOutBox>) = jpa.deleteAll(outboxes)

    override fun saveAllHistory(history: List<MemberOutBoxHistory>) { historyJpa.saveAll(history) }
}
