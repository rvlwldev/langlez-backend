package com.langlez.outbox

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
internal class OutBoxRepository(
    private val jpa: OutBoxJpaRepository,
    private val historyJpa: HistoryRepository
) {

    fun save(outbox: OutBox): OutBox =
        jpa.save(outbox)

    fun saveAll(outboxes: List<OutBox>): List<OutBox> =
        jpa.saveAll(outboxes)

    fun findAllTargetToDispatch(): List<OutBox> =
        jpa.findAllByStatusInOrderByCreatedAtAsc(listOf(OutBox.Status.READY, OutBox.Status.PROCESSING))

    fun findTargetToDispatch(limit: Int): List<OutBox> =
        jpa.findAllByStatusInOrderByCreatedAtAsc(listOf(OutBox.Status.READY, OutBox.Status.PROCESSING), org.springframework.data.domain.PageRequest.of(0, limit))

    fun findAllCompleted(): List<OutBox> =
        jpa.findAllByStatus(OutBox.Status.COMPLETE)

    fun deleteAll(outboxes: List<OutBox>) =
        jpa.deleteAll(outboxes)

    fun saveAllHistory(history: List<OutBoxHistory>) {
        historyJpa.saveAll(history)
    }

}

internal interface OutBoxJpaRepository : JpaRepository<OutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(statuses: List<OutBox.Status>): List<OutBox>
    fun findAllByStatusInOrderByCreatedAtAsc(statuses: List<OutBox.Status>, pageable: org.springframework.data.domain.Pageable): List<OutBox>
    fun findAllByStatus(status: OutBox.Status): List<OutBox>
}

internal interface HistoryRepository : JpaRepository<OutBoxHistory, Long>
