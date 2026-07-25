package com.langlez.echo.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.echo.infrastructure.outbox.jpa.EchoOutBoxHistoryJpaRepository
import com.langlez.echo.infrastructure.outbox.jpa.EchoOutBoxJpaRepository
import com.langlez.mysql.outbox.OutBoxStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class EchoOutBoxRepositoryImpl(
    private val jpa: EchoOutBoxJpaRepository,
    private val historyJpa: EchoOutBoxHistoryJpaRepository,
    private val mapper: ObjectMapper,
) : EchoOutBoxRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): EchoOutBox =
        jpa.save(EchoOutBox(aggregateType, aggregateId, eventName, mapper.writeValueAsString(payload)))

    override fun findToDispatch(limit: Int): List<EchoOutBox> =
        jpa.findAllByStatusInOrderByCreatedAtAsc(
            listOf(OutBoxStatus.READY, OutBoxStatus.PROCESSING),
            PageRequest.of(0, limit),
        )

    override fun findCompletedOrFailed(limit: Int): List<EchoOutBox> =
        jpa.findAllByStatusIn(
            listOf(OutBoxStatus.COMPLETE, OutBoxStatus.FAILED),
            PageRequest.of(0, limit),
        )

    override fun saveAll(outboxes: List<EchoOutBox>): List<EchoOutBox> = jpa.saveAll(outboxes)

    override fun deleteAll(outboxes: List<EchoOutBox>) = jpa.deleteAll(outboxes)

    override fun saveAllHistory(history: List<EchoOutBoxHistory>) { historyJpa.saveAll(history) }
}
