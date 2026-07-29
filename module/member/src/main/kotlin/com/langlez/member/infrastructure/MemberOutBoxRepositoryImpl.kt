package com.langlez.member.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.member.infrastructure.jpa.MemberOutBoxHistoryJpaRepository
import com.langlez.member.infrastructure.jpa.MemberOutBoxJpaRepository
import com.langlez.mysql.outbox.OutBoxRepository
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
) : OutBoxRepository<MemberOutBox, MemberOutBoxHistory> {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(domain: String, topic: String, payload: String?, key: Any?): MemberOutBox {
        val payloadString = payload ?: ""
        val keyString = key?.toString()
        return jpa.save(MemberOutBox(domain, topic, payloadString, keyString))
    }

    override fun save(outbox: MemberOutBox): MemberOutBox = jpa.save(outbox)

    override fun findToDispatch(limit: Int): List<MemberOutBox> =
        jpa.findAllByStatusInOrderByCreatedAtAsc(
            listOf(OutBoxStatus.READY, OutBoxStatus.PROCESSING),
            PageRequest.of(0, limit),
        )

    override fun findAllProcessed(limit: Int): List<MemberOutBox> =
        jpa.findAllByStatusIn(
            listOf(OutBoxStatus.COMPLETE, OutBoxStatus.FAILED),
            PageRequest.of(0, limit),
        )

    override fun saveAll(outboxes: List<MemberOutBox>): List<MemberOutBox> = jpa.saveAll(outboxes)
    override fun deleteAll(outboxes: List<MemberOutBox>) = jpa.deleteAll(outboxes)
    override fun saveHistory(history: MemberOutBoxHistory): MemberOutBoxHistory = historyJpa.save(history)
    override fun saveAllHistory(history: List<MemberOutBoxHistory>) {
        historyJpa.saveAll(history)
    }
}
