package com.langlez.chat.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.chat.infrastructure.outbox.jpa.ChatOutBoxHistoryJpaRepository
import com.langlez.chat.infrastructure.outbox.jpa.ChatOutBoxJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class ChatOutBoxRepositoryImpl(
    private val jpa: ChatOutBoxJpaRepository,
    private val historyJpa: ChatOutBoxHistoryJpaRepository,
    private val mapper: ObjectMapper,
) : ChatOutBoxRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregateType: String, aggregateId: String, eventName: String, payload: Any?): ChatOutBox =
        jpa.save(ChatOutBox(aggregateType, aggregateId, eventName, mapper.writeValueAsString(payload)))

    override fun findToDispatch(limit: Int): List<ChatOutBox> =
        jpa.findAllByStatusInOrderByCreatedAtAsc(
            listOf(ChatOutBox.Status.READY, ChatOutBox.Status.PROCESSING),
            PageRequest.of(0, limit),
        )

    override fun findAllCompleted(): List<ChatOutBox> =
        jpa.findAllByStatus(ChatOutBox.Status.COMPLETE)

    override fun saveAll(outboxes: List<ChatOutBox>): List<ChatOutBox> = jpa.saveAll(outboxes)

    override fun deleteAll(outboxes: List<ChatOutBox>) = jpa.deleteAll(outboxes)

    override fun saveAllHistory(history: List<ChatOutBoxHistory>) { historyJpa.saveAll(history) }
}
