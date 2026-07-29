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
    override fun save(domain: String, topic: String, payload: String?, key: Any?): EchoOutBox {
        val payloadString = payload ?: ""
        val keyString = key?.toString()
        return jpa.save(EchoOutBox(domain, topic, payloadString, keyString))
    }

    override fun save(outbox: EchoOutBox): EchoOutBox = jpa.save(outbox)

    override fun findToDispatch(limit: Int): List<EchoOutBox> =
        jpa.findAllByStatusInOrderByCreatedAtAsc(
            listOf(OutBoxStatus.READY, OutBoxStatus.PROCESSING),
            PageRequest.of(0, limit),
        )

    override fun findAllProcessed(limit: Int): List<EchoOutBox> =
        jpa.findAllByStatusIn(
            listOf(OutBoxStatus.COMPLETE, OutBoxStatus.FAILED),
            PageRequest.of(0, limit),
        )

    override fun saveAll(outboxes: List<EchoOutBox>): List<EchoOutBox> = jpa.saveAll(outboxes)

    override fun deleteAll(outboxes: List<EchoOutBox>) { jpa.deleteAll(outboxes) }

    override fun saveHistory(history: EchoOutBoxHistory): EchoOutBoxHistory = historyJpa.save(history)

    override fun saveAllHistory(history: List<EchoOutBoxHistory>) { historyJpa.saveAll(history) }
}
